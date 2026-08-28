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

public class SetSimJoin {

	// =========================================================================
	// Phase 1 Mapper: 아이템별로 (rid:set_size) 방출
	// =========================================================================
	public static class InvertedListMapper extends Mapper<Object, Text, Text, Text> {
		private Text rid = new Text();
		private Text item = new Text();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			StringTokenizer itr = new StringTokenizer(line);
			if (!itr.hasMoreTokens()) return;

			String recordId = itr.nextToken();
			List<String> items = new ArrayList<>();
			while (itr.hasMoreTokens()) {
				items.add(itr.nextToken());
			}

			int size = items.size();
			rid.set(recordId + ":" + size);

			for (String it : items) {
				item.set(it);
				context.write(item, rid); // emit (item, "rid:size")
			}
		}
	}

	// =========================================================================
	// Phase 1 Reducer: 같은 아이템을 가진 유저 쌍에 대해 ("id1 id2 size1 size2", 1) 방출
	// =========================================================================
	public static class InvertedListReducer extends Reducer<Text, Text, Text, IntWritable> {
		private static IntWritable one = new IntWritable(1);
		private Text pairKey = new Text();

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			Vector<String> str = new Vector<String>();
			for (Text val : values) {
				str.add(val.toString());
			}

			for (int i = 0; i < str.size(); i++) {
				for (int j = i + 1; j < str.size(); j++) {
					String[] t1 = str.get(i).split(":");
					String[] t2 = str.get(j).split(":");

					int id1 = Integer.parseInt(t1[0]);
					int size1 = Integer.parseInt(t1[1]);
					int id2 = Integer.parseInt(t2[0]);
					int size2 = Integer.parseInt(t2[1]);

					if (id1 > id2) {
						int tmpId = id1; id1 = id2; id2 = tmpId;
						int tmpSize = size1; size1 = size2; size2 = tmpSize;
					}

					pairKey.set(id1 + "\t" + id2 + "\t" + size1 + "\t" + size2);
					context.write(pairKey, one);
				}
			}
		}
	}

	// =========================================================================
	// Phase 2 Mapper: Phase 1 중간 결과를 읽어 ("id1 id2 size1 size2", count) 방출
	// =========================================================================
	public static class SimMapper extends Mapper<Object, Text, Text, IntWritable> {
		private Text pair = new Text();
		private IntWritable count = new IntWritable();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length >= 5) {
				pair.set(tokens[0] + "\t" + tokens[1] + "\t" + tokens[2] + "\t" + tokens[3]);
				count.set(Integer.parseInt(tokens[4]));
				context.write(pair, count);
			}
		}
	}

	// =========================================================================
	// Phase 2 Reducer: 자카드 유사도 = common / (size1 + size2 - common) >= threshold 필터링
	// =========================================================================
	public static class SimReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private static double sigma;
		private Text outKey = new Text();
		private IntWritable outVal = new IntWritable();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			sigma = config.getFloat("threshold", -1);
		}

		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			int common = 0;
			for (IntWritable val : values) {
				common += val.get();
			}

			String[] tokens = key.toString().split("\\s+");
			int size1 = Integer.parseInt(tokens[2]);
			int size2 = Integer.parseInt(tokens[3]);

			// Jaccard Similarity 계산: 교집합 / 합집합
			double jaccard = (double) common / (size1 + size2 - common);

			if (jaccard >= sigma) {
				outKey.set(tokens[0] + "\t" + tokens[1]);
				outVal.set(common);
				context.write(outKey, outVal);
			}
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		FileSystem fs = FileSystem.get(conf);
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 3) {
			System.out.println("usage: SetSimJoin <threshold> <in> <out>");
			System.exit(2);
		}

		conf.setFloat("threshold", (float) Double.parseDouble(otherArgs[0]));

		Path inputPath = new Path(otherArgs[1]);
		Path outputPath = new Path(otherArgs[2]);
		Path pathtmp = new Path("setSimJoinTmp");

		if (fs.exists(pathtmp)) fs.delete(pathtmp, true);
		if (fs.exists(outputPath)) fs.delete(outputPath, true);

		// Run Phase 1 Job
		Job job1 = new Job(conf, "buildInvertedList");
		job1.setJarByClass(SetSimJoin.class);
		job1.setMapperClass(InvertedListMapper.class);
		job1.setReducerClass(InvertedListReducer.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(Text.class);
		job1.setNumReduceTasks(2);
		FileInputFormat.addInputPath(job1, inputPath);
		FileOutputFormat.setOutputPath(job1, pathtmp);
		if (!job1.waitForCompletion(true)) System.exit(1);

		// Run Phase 2 Job (Combiner 제거하여 정확한 합산 후 임계치 필터링)
		Job job2 = new Job(conf, "setSimilarityJoin");
		job2.setJarByClass(SetSimJoin.class);
		job2.setMapperClass(SimMapper.class);
		job2.setReducerClass(SimReducer.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(IntWritable.class);
		job2.setNumReduceTasks(2);
		FileInputFormat.addInputPath(job2, pathtmp);
		FileOutputFormat.setOutputPath(job2, outputPath);
		System.exit(job2.waitForCompletion(true) ? 0 : 1);
	}
}
