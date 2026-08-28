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
import org.apache.hadoop.util.*;

public class VecSimJoin {

	// =========================================================================
	// Mapper: 2차원 상삼각 격자(Upper Triangular) 분할 방 배정
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private int numberOfPartitions = 4;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			numberOfPartitions = configuration.getInt("numberOfPartitions", 4);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] arr = line.split("\\s+");
			if (arr.length < 2) return;

			int rid = Integer.parseInt(arr[0]);
			int pId = Math.abs(rid) % numberOfPartitions;

			// 가로줄(Row) 방들로 전송: (pId, j) for j in [pId, numberOfPartitions - 1]
			for (int j = pId; j < numberOfPartitions; j++) {
				emitkey.set(pId + "," + j);
				emitval.set("A:" + line);
				context.write(emitkey, emitval);
			}

			// 세로줄(Column) 방들로 전송: (i, pId) for i in [0, pId]
			for (int i = 0; i <= pId; i++) {
				emitkey.set(i + "," + pId);
				emitval.set("B:" + line);
				context.write(emitkey, emitval);
			}
		}
	}

	// =========================================================================
	// Reducer: 각 격자 방 안에서 거리(Threshold) 이내의 모든 벡터 쌍 추출
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private float threshold;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Reducer.Context context) {
			Configuration configuration = context.getConfiguration();
			threshold = configuration.getFloat("threshold", 0.0f);
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

			if (row == col) {
				// 대각선 방: 같은 파티션 내의 점들끼리 상호 비교 (중복/자기자신 제외)
				for (int a = 0; a < vecs1.size(); a++) {
					for (int b = a + 1; b < vecs1.size(); b++) {
						String p1 = vecs1.get(a);
						String p2 = vecs1.get(b);
						double d = dist(p1, p2);
						if (d <= threshold) {
							emitkey.set(p1);
							emitval.set(p2 + "\t" + d);
							context.write(emitkey, emitval);
						}
					}
				}
			} else {
				// 비대각선 방: vecs1(Row 파티션)과 vecs2(Col 파티션) 간의 1:1 비교
				for (String p1 : vecs1) {
					for (String p2 : vecs2) {
						double d = dist(p1, p2);
						if (d <= threshold) {
							emitkey.set(p1);
							emitval.set(p2 + "\t" + d);
							context.write(emitkey, emitval);
						}
					}
				}
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
	// Main
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 4) {
			System.out.println("usage: VecSimJoin <numberOfPartitions> <threshold> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output1 = new Path(otherArgs[3]);
		if (hdfs.exists(output1)) hdfs.delete(output1, true);

		conf.setInt("numberOfPartitions", Integer.parseInt(otherArgs[0]));
		conf.setFloat("threshold", (float) Double.parseDouble(otherArgs[1]));

		Job job = new Job(conf, "bruteforce-vecsimjoin");
		job.setJarByClass(VecSimJoin.class);
		job.setNumReduceTasks(2);
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(otherArgs[2]));
		FileOutputFormat.setOutputPath(job, output1);

		if (!job.waitForCompletion(true)) System.exit(1);
	}
}
