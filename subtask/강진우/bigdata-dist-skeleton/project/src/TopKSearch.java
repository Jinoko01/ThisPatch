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

public class TopKSearch {

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
	// Phase 1 Mapper: 레코드 ID를 해싱하여 여러 파티션으로 균등 분배
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private int numOfPartitions = 4;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Mapper.Context context) {
			Configuration conf = context.getConfiguration();
			numOfPartitions = conf.getInt("numberOfPartitions", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String arr[] = line.split("\\s+", 2);
			if (arr.length < 2) return;

			int rid = Integer.parseInt(arr[0]);
			int pid = Math.abs(rid) % numOfPartitions;

			emitkey.set(Integer.toString(pid));
			emitval.set(line);
			context.write(emitkey, emitval);
		}
	}

	// =========================================================================
	// Phase 1 Reducer: 각 파티션 안에서 쿼리 점과 가장 가까운 로컬 Top-K 추출
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private int K;
		private String query;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Reducer.Context context) {
			Configuration conf = context.getConfiguration();
			K = conf.getInt("K", 2);
			query = conf.get("queryPoint", "");
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			PriorityQueue<MyType> queue = new PriorityQueue<MyType>();

			for (Text val : values) {
				String pointStr = val.toString();
				double d = dist(query, pointStr);
				queue.add(new MyType(d, pointStr));
				if (queue.size() > K) {
					queue.poll();
				}
			}

			while (!queue.isEmpty()) {
				MyType item = queue.poll();
				emitkey.set(item.str);
				emitval.set(Double.toString(item.dist));
				context.write(emitkey, emitval);
			}
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
	// Phase 2 Mapper: 모든 로컬 Top-K 후보들을 단일 Reducer로 전송
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
	// Phase 2 Reducer: 전역 최종 Top-K 결정 및 정렬 출력
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
			PriorityQueue<MyType> queue = new PriorityQueue<MyType>();

			for (Text val : values) {
				String line = val.toString();
				String[] tokens = line.split("\\s+");
				if (tokens.length < 2) continue;

				double d = Double.parseDouble(tokens[tokens.length - 1]);
				queue.add(new MyType(d, line));
				if (queue.size() > K) {
					queue.poll();
				}
			}

			List<MyType> resultList = new ArrayList<>();
			while (!queue.isEmpty()) {
				resultList.add(queue.poll());
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

		if (otherArgs.length != 6) {
			System.out.println("usage: TopKSearch <numberOfPartitions> <queryPoint> <K> <in> <out1> <out2>");
			System.exit(1);
		}

		conf.setInt("numberOfPartitions", Integer.parseInt(otherArgs[0]));
		String arr[] = otherArgs[1].split(":");
		String query = "0";
		for (int i = 0; i < arr.length; i++) {
			query = query + "\t" + arr[i];
		}
		conf.set("queryPoint", query);
		conf.setInt("K", Integer.parseInt(otherArgs[2]));

		FileSystem hdfs = FileSystem.get(conf);
		Path output1 = new Path(otherArgs[4]);
		Path output2 = new Path(otherArgs[5]);

		if (hdfs.exists(output1)) hdfs.delete(output1, true);
		if (hdfs.exists(output2)) hdfs.delete(output2, true);

		// Phase 1
		Job job1 = new Job(conf, "topk-search-phase1");
		job1.setJarByClass(TopKSearch.class);
		job1.setNumReduceTasks(Integer.parseInt(otherArgs[0]));
		job1.setMapperClass(MapClass1.class);
		job1.setReducerClass(ReduceClass1.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job1, new Path(otherArgs[3]));
		FileOutputFormat.setOutputPath(job1, output1);
		if (!job1.waitForCompletion(true)) System.exit(1);

		// Phase 2
		Job job2 = new Job(conf, "topk-search-phase2");
		job2.setJarByClass(TopKSearch.class);
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
