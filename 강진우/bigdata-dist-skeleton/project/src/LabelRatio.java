package ssafy;

import java.io.IOException;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class LabelRatio {

	// =========================================================================
	// Phase 1: 단어:라벨 쌍의 빈도수 계산 (WordCount와 동일)
	// =========================================================================
	public static class TokenizerMapper extends Mapper<Object, Text, Text, IntWritable> {
		private final static IntWritable one = new IntWritable(1);
		private Text wordLabel = new Text();

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "profits:spam"
			String line = value.toString().trim();
			if (!line.isEmpty()) {
				wordLabel.set(line);
				context.write(wordLabel, one); // yield ("profits:spam", 1)
			}
		}
	}

	public static class IntSumReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private IntWritable result = new IntWritable();

		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			// key: "profits:spam", values: [1, 1, 1, ...]
			int sum = 0;
			for (IntWritable val : values) {
				sum += val.get();
			}
			result.set(sum);
			context.write(key, result); // yield ("profits:spam", 10)
		}
	}

	// =========================================================================
	// Phase 2: 단어(Word)별로 모아서 스팸/일반 비율 계산
	// =========================================================================
	public static class M2Mapper extends Mapper<Object, Text, Text, Text> {
		private Text wordKey = new Text();
		private Text labelCountVal = new Text();

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// Phase 1의 출력 라인 예시: "profits:spam\t10"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+"); // [0]: "profits:spam", [1]: "10"
			if (tokens.length >= 2) {
				String[] pair = tokens[0].split(":"); // [0]: "profits", [1]: "spam"
				if (pair.length == 2) {
					wordKey.set(pair[0]); // Key: "profits"
					labelCountVal.set(pair[1] + "\t" + tokens[1]); // Value: "spam\t10"
					context.write(wordKey, labelCountVal);
				}
			}
		}
	}

	public static class M2Reducer extends Reducer<Text, Text, Text, Text> {
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			// key: "profits"
			// values: ["spam\t10", "normal\t2", ...]

			int totalCount = 0;
			List<String> records = new ArrayList<>();

			// 1차 순회: 전체 합계(totalCount) 계산 및 리스트 저장
			for (Text val : values) {
				String strVal = val.toString();
				records.add(strVal);
				String[] parts = strVal.split("\\s+"); // [0]: "spam", [1]: "10"
				totalCount += Integer.parseInt(parts[1]);
			}

			// 2차 순회: 각 라벨별 비율(ratio) 계산 후 방출
			for (String rec : records) {
				String[] parts = rec.split("\\s+");
				String label = parts[0];
				int count = Integer.parseInt(parts[1]);
				double ratio = (double) count / totalCount;

				// 최종 출력 형식: Key = "profits", Value = "spam\t10\t0.8333"
				String outStr = String.format("%s\t%d\t%.4f", label, count, ratio);
				context.write(key, new Text(outStr));
			}
		}
	}

	// =========================================================================
	// Main: 2개의 MapReduce Job을 순차 실행 (Job Chaining)
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 3) {
			System.err.println("Usage: LabelRatio <in> <out1> <out2>");
			System.exit(2);
		}

		Path output1 = new Path(otherArgs[1]);
		Path output2 = new Path(otherArgs[2]);
		FileSystem fs = FileSystem.get(conf);

		if (fs.exists(output1)) {
			fs.delete(output1, true);
		}
		if (fs.exists(output2)) {
			fs.delete(output2, true);
		}

		// ---------------------------------------------------------------------
		// [Job 1 실행]: 단어:라벨 빈도수 집계
		// ---------------------------------------------------------------------
		Job job1 = new Job(conf, "label ratio phase1");
		job1.setJarByClass(LabelRatio.class);
		job1.setMapperClass(TokenizerMapper.class);
		job1.setCombinerClass(IntSumReducer.class);
		job1.setReducerClass(IntSumReducer.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);
		job1.setNumReduceTasks(1);

		FileInputFormat.addInputPath(job1, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job1, output1);
		boolean ok1 = job1.waitForCompletion(true);
		if (!ok1) {
			System.exit(1);
		}

		// ---------------------------------------------------------------------
		// [Job 2 실행]: 단어별 라벨 비율 계산
		// ---------------------------------------------------------------------
		Job job2 = new Job(conf, "label ratio phase2");
		job2.setJarByClass(LabelRatio.class);
		job2.setMapperClass(M2Mapper.class);
		job2.setReducerClass(M2Reducer.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(Text.class);
		job2.setNumReduceTasks(1);

		FileInputFormat.addInputPath(job2, output1); // Job 1의 출력이 Job 2의 입력이 됨!
		FileOutputFormat.setOutputPath(job2, output2);
		boolean ok2 = job2.waitForCompletion(true);
		if (!ok2) {
			System.exit(1);
		}
	}
}
