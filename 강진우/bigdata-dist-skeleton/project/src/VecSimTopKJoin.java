package ssafy;

import java.io.*;
import java.util.*;
import java.lang.Math;

import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class VecSimTopKJoin {

	// =========================================================================
	// Max-Heap 유지를 위한 MyType
	// =========================================================================
	public static class MyType implements Comparable<MyType> {
		public double dist;
		public String str;

		public MyType(double d, String s) {
			this.dist = d;
			this.str = s;
		}

		@Override
		public int compareTo(MyType o) {
			return this.dist > o.dist ? -1 : (this.dist < o.dist ? 1 : 0);
		}
	}

	// =========================================================================
	// Phase 1 Mapper: 2D 상삼각 격자 분할 방 배정
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private int numberOfPartitions = 4;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Mapper.Context context) {
			Configuration conf = context.getConfiguration();
			numberOfPartitions = conf.getInt("numberOfPartitions", 4);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] arr = line.split("\\s+");
			if (arr.length < 2) return;

			int rid = Integer.parseInt(arr[0]);
			int pId = Math.abs(rid) % numberOfPartitions;

			// 가로줄(Row) 방들로 전송: (pId, j)
			for (int j = pId; j < numberOfPartitions; j++) {
				emitkey.set(pId + "," + j);
				emitval.set("A:" + line);
				context.write(emitkey, emitval);
			}

			// 세로줄(Column) 방들로 전송: (i, pId)
			for (int i = 0; i <= pId; i++) {
				emitkey.set(i + "," + pId);
				emitval.set("B:" + line);
				context.write(emitkey, emitval);
			}
		}
	}

	// =========================================================================
	// Phase 1 Reducer: 각 격자 방 안에서 거리 기준 로컬 Top-K 쌍 추출
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private int K;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Reducer.Context context) {
			Configuration conf = context.getConfiguration();
			K = conf.getInt("K", 2);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			String[] parts = key.toString().split(",");
			int row = Integer.parseInt(parts[0]);
			int col = Integer.parseInt(parts[1]);

			Vector<String> vecs1 = new Vector<String>();
			Vector<String> vecs2 = new Vector<String>();

			for (Text val : values) {
				String str = val.toString();
				if (str.startsWith("A:")) {
					vecs1.add(str.substring(2));
				} else if (str.startsWith("B:")) {
					vecs2.add(str.substring(2));
				}
			}

			PriorityQueue<MyType> pq = new PriorityQueue<MyType>();

			if (row == col) {
				// 대각선 방
				for (int a = 0; a < vecs1.size(); a++) {
					for (int b = a + 1; b < vecs1.size(); b++) {
						String p1 = vecs1.get(a);
						String p2 = vecs1.get(b);
						double d = dist(p1, p2);
						pq.add(new MyType(d, p1 + "\t" + p2));
						if (pq.size() > K) {
							pq.poll();
						}
					}
				}
			} else {
				// 비대각선 방
				for (String p1 : vecs1) {
					for (String p2 : vecs2) {
						double d = dist(p1, p2);
						pq.add(new MyType(d, p1 + "\t" + p2));
						if (pq.size() > K) {
							pq.poll();
						}
					}
				}
			}

			while (!pq.isEmpty()) {
				MyType item = pq.poll();
				emitkey.set(item.str);
				emitval.set(Double.toString(item.dist));
				context.write(emitkey, emitval);
			}

			vecs1.clear();
			vecs2.clear();
		}

		public static double dist(String sp1, String sp2) {
			String[] strarr1 = sp1.split("\\s+");
			String[] strarr2 = sp2.split("\\s+");
			double d1, d2, diff, sum = 0;
			// 0번 인덱스는 ID -> 1번 인덱스부터 좌표 차원
			for (int i = 1; i < strarr1.length && i < strarr2.length; i++) {
				d1 = Double.parseDouble(strarr1[i]);
				d2 = Double.parseDouble(strarr2[i]);
				diff = d1 - d2;
				sum += diff * diff;
			}
			return Math.sqrt(sum);
		}
	}

	// =========================================================================
	// Phase 2 Mapper: 모든 로컬 Top-K 쌍들을 단일 Reducer로 전송
	// =========================================================================
	public static class MapClass2 extends Mapper<Object, Text, Text, Text> {
		private Text emitkey = new Text("global_topk");
		private Text emitval = new Text();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			emitval.set(line);
			context.write(emitkey, emitval);
		}
	}

	// =========================================================================
	// Phase 2 Reducer: 전역 최종 Top-K 가장 가까운 벡터 쌍 결정
	// =========================================================================
	public static class ReduceClass2 extends Reducer<Text, Text, Text, Text> {
		private int K;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Reducer.Context context) {
			Configuration conf = context.getConfiguration();
			K = conf.getInt("K", 2);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			PriorityQueue<MyType> pq = new PriorityQueue<MyType>();

			for (Text val : values) {
				String line = val.toString();
				String[] tokens = line.split("\\s+");
				if (tokens.length < 2) continue;

				double d = Double.parseDouble(tokens[tokens.length - 1]);
				pq.add(new MyType(d, line));
				if (pq.size() > K) {
					pq.poll();
				}
			}

			List<MyType> resultList = new ArrayList<>();
			while (!pq.isEmpty()) {
				resultList.add(pq.poll());
			}
			Collections.reverse(resultList);

			for (MyType item : resultList) {
				emitkey.set(item.str);
				emitval.set("");
				context.write(emitkey, emitval);
			}
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 5) {
			System.out.println("usage: VecSimTopKJoin <numberOfPartitions> <K> <in> <out1> <out2>");
			System.exit(1);
		}

		conf.setInt("numberOfPartitions", Integer.parseInt(otherArgs[0]));
		conf.setInt("K", Integer.parseInt(otherArgs[1]));

		FileSystem hdfs = FileSystem.get(conf);
		Path output1 = new Path(otherArgs[3]);
		Path output2 = new Path(otherArgs[4]);
		if (hdfs.exists(output1)) hdfs.delete(output1, true);
		if (hdfs.exists(output2)) hdfs.delete(output2, true);

		// Phase 1
		Job job = new Job(conf, "topkjoin-phase1");
		job.setJarByClass(VecSimTopKJoin.class);
		job.setNumReduceTasks(Integer.parseInt(otherArgs[0]));
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(otherArgs[2]));
		FileOutputFormat.setOutputPath(job, output1);
		if (!job.waitForCompletion(true)) System.exit(1);

		// Phase 2
		Job job2 = new Job(conf, "topkjoin-phase2");
		job2.setJarByClass(VecSimTopKJoin.class);
		job2.setNumReduceTasks(1);
		job2.setMapperClass(MapClass2.class);
		job2.setReducerClass(ReduceClass2.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job2, output1);
		FileOutputFormat.setOutputPath(job2, output2);
		System.exit(job2.waitForCompletion(true) ? 0 : 1);
	}
}
