package ssafy;

import java.io.*;
import java.util.*;

import org.apache.hadoop.conf.*;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.*;

public class CommonItemCount {

	// =========================================================================
	// Phase 1 Mapper: 역색인(Inverted Index) 생성 -> Key: item, Value: rid
	// =========================================================================
	public static class InvertedIndexMapper extends Mapper<Object, Text, Text, Text> {
		private Text rid = new Text();
		private Text item = new Text();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			StringTokenizer itr = new StringTokenizer(line);
			if (!itr.hasMoreTokens()) return;

			// 첫 번째 토큰: 레코드 ID (User ID)
			String recordId = itr.nextToken();
			rid.set(recordId);

			// 나머지 토큰들: 해당 레코드가 가진 아이템들
			while (itr.hasMoreTokens()) {
				item.set(itr.nextToken());
				context.write(item, rid); // emit (item, rid)
			}
		}
	}

	// =========================================================================
	// Phase 1 Reducer: 같은 아이템을 공유하는 모든 (rid_i, rid_j) 쌍에 대해 1 방출
	// =========================================================================
	public static class InvertedIndexReducer extends Reducer<Text, Text, Text, IntWritable> {
		private Text ridpair = new Text();
		private static IntWritable one = new IntWritable(1);

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			Vector<String> str = new Vector<String>();
			for (Text val : values) {
				str.add(val.toString());
			}

			// 같은 아이템을 가진 유저들끼리 2명씩 짝지어 (rid_i, rid_j, 1) 방출
			for (int i = 0; i < str.size(); i++) {
				for (int j = i + 1; j < str.size(); j++) {
					String r1 = str.get(i);
					String r2 = str.get(j);

					// 중복 방지를 위해 r1 < r2 정렬 유지
					if (Integer.parseInt(r1) > Integer.parseInt(r2)) {
						String tmp = r1;
						r1 = r2;
						r2 = tmp;
					}

					ridpair.set(r1 + "\t" + r2);
					context.write(ridpair, one);
				}
			}
		}
	}

	// =========================================================================
	// Phase 2 Mapper: (rid_i, rid_j) 쌍을 Key로 전달
	// =========================================================================
	public static class SimMapper extends Mapper<Object, Text, Text, IntWritable> {
		private Text ridpair = new Text();
		private IntWritable count = new IntWritable();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length >= 3) {
				ridpair.set(tokens[0] + "\t" + tokens[1]);
				count.set(Integer.parseInt(tokens[2]));
				context.write(ridpair, count);
			}
		}
	}

	// =========================================================================
	// Phase 2 Reducer: 두 유저가 공유하는 공통 아이템 총 개수 합산
	// =========================================================================
	public static class SimReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private IntWritable count = new IntWritable();

		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			int sum = 0;
			for (IntWritable val : values) {
				sum += val.get();
			}
			count.set(sum);
			context.write(key, count);
		}
	}

	// =========================================================================
	// Main: Phase 1 -> Phase 2 순차 실행
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(conf);
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 3) {
			System.out.println("usage: CommonItemCount <in> <out1> <out2>");
			System.exit(2);
		}

		Path inputPath = new Path(otherArgs[0]);
		Path pathtmp = new Path(otherArgs[1]);
		Path finalOut = new Path(otherArgs[2]);

		if (fs.exists(pathtmp)) fs.delete(pathtmp, true);
		if (fs.exists(finalOut)) fs.delete(finalOut, true);

		// Run Phase 1 Job: Inverted Index & Candidate Pairs
		Job job1 = new Job(conf, "buildSetInvertedIndex");
		job1.setJarByClass(CommonItemCount.class);
		job1.setMapperClass(InvertedIndexMapper.class);
		job1.setReducerClass(InvertedIndexReducer.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(Text.class);
		job1.setNumReduceTasks(2);
		FileInputFormat.addInputPath(job1, inputPath);
		FileOutputFormat.setOutputPath(job1, pathtmp);
		if (!job1.waitForCompletion(true)) {
			System.exit(1);
		}

		// Run Phase 2 Job: Sum Co-occurrences
		Job job2 = new Job(conf, "countCommonItem");
		job2.setJarByClass(CommonItemCount.class);
		job2.setMapperClass(SimMapper.class);
		job2.setCombinerClass(SimReducer.class);
		job2.setReducerClass(SimReducer.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(IntWritable.class);
		job2.setNumReduceTasks(2);
		FileInputFormat.addInputPath(job2, pathtmp);
		FileOutputFormat.setOutputPath(job2, finalOut);
		System.exit(job2.waitForCompletion(true) ? 0 : 1);
	}
}
