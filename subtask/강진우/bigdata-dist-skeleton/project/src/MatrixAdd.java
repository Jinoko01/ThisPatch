package ssafy;

import java.io.IOException;
import java.util.StringTokenizer;

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

public class MatrixAdd {

	// =========================================================================
	// Mapper: 각 행렬의 원소에서 (행, 열) 좌표를 Key로, 원소 값을 Value로 방출
	// =========================================================================
	public static class MAddMapper extends Mapper<Object, Text, Text, IntWritable> {
		private Text coordKey = new Text();
		private IntWritable val = new IntWritable();

		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "A\t0\t0\t3" 또는 "B\t0\t0\t2"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+"); // [0]: 행렬명, [1]: row, [2]: col, [3]: value
			if (tokens.length >= 4) {
				String row = tokens[1];
				String col = tokens[2];
				int elementValue = Integer.parseInt(tokens[3]);

				coordKey.set(row + "\t" + col); // Key: "0\t0"
				val.set(elementValue);          // Value: 3
				context.write(coordKey, val);   // yield ("0\t0", 3)
			}
		}
	}

	// =========================================================================
	// Reducer: 동일한 (행, 열) 좌표에 모인 값들을 단순히 덧셈(+)하여 최종 행렬 C의 원소 생성
	// =========================================================================
	public static class MAddReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private IntWritable result = new IntWritable();

		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			// key: "0\t0", values: [3, 2] (A의 0행0열 값 3과 B의 0행0열 값 2가 모임)
			int sum = 0;
			for (IntWritable v : values) {
				sum += v.get();
			}
			result.set(sum);
			context.write(key, result); // yield ("0\t0", 5)
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 2) {
			System.err.println("Usage: MatrixAdd <in> <out>");
			System.exit(2);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[1]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "matrix addition");
		job.setJarByClass(MatrixAdd.class);
		job.setMapperClass(MAddMapper.class);
		job.setCombinerClass(MAddReducer.class); // 덧셈은 결합법칙이 성립하므로 Combiner 적용 가능!
		job.setReducerClass(MAddReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setNumReduceTasks(2); // 2개의 Reducer로 분산 처리

		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
