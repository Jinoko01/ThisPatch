package ssafy;

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class MatrixMulti {

	// =========================================================================
	// Mapper: 1-Phase 복제 방출 (Text 입출력 형식)
	// =========================================================================
	public static class MMMapper extends Mapper<Object, Text, Text, Text> {
		private String Matrix1name;
		private String Matrix2name;
		private int n;
		private int m;

		private Text outKey = new Text();
		private Text outVal = new Text();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			Matrix1name = config.get("Matrix1name");
			Matrix2name = config.get("Matrix2name");
			n = config.getInt("n", 2);
			m = config.getInt("m", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			StringTokenizer token = new StringTokenizer(value.toString());
			if (!token.hasMoreTokens()) return;

			String mat = token.nextToken();
			int row = Integer.parseInt(token.nextToken());
			int col = Integer.parseInt(token.nextToken());
			int val = Integer.parseInt(token.nextToken());

			if (mat.equals(Matrix1name)) {
				// A(i, k, val) -> C의 모든 j열로 복제
				for (int j = 0; j < m; j++) {
					outKey.set(row + "\t" + j);
					outVal.set("A\t" + col + "\t" + val);
					context.write(outKey, outVal);
				}
			} else if (mat.equals(Matrix2name)) {
				// B(k, j, val) -> C의 모든 i행으로 복제
				for (int i = 0; i < n; i++) {
					outKey.set(i + "\t" + col);
					outVal.set("B\t" + row + "\t" + val);
					context.write(outKey, outVal);
				}
			}
		}
	}

	// =========================================================================
	// Reducer: 내적 계산 후 (i, j, sum)을 Text 형태로 방출
	// =========================================================================
	public static class MMReducer extends Reducer<Text, Text, Text, Text> {
		private int l;
		private String Matrix1name;
		private String Matrix2name;
		private Text outVal = new Text();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			Matrix1name = config.get("Matrix1name");
			Matrix2name = config.get("Matrix2name");
			l = config.getInt("l", 2);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			int[] a = new int[l];
			int[] b = new int[l];

			for (Text val : values) {
				StringTokenizer st = new StringTokenizer(val.toString());
				String mat = st.nextToken();
				int k = Integer.parseInt(st.nextToken());
				int v = Integer.parseInt(st.nextToken());

				if (mat.equals(Matrix1name)) {
					a[k] = v;
				} else if (mat.equals(Matrix2name)) {
					b[k] = v;
				}
			}

			int sum = 0;
			for (int k = 0; k < l; k++) {
				sum += a[k] * b[k];
			}

			outVal.set(String.valueOf(sum));
			context.write(key, outVal);
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 7) {
			System.err.println("Usage: MatrixMulti <Matrix 1 name> <Matrix 2 name> <n> <l> <m> <in> <out>");
			System.exit(2);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[6]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "matrix multiplication");
		Configuration config = job.getConfiguration();
		config.set("Matrix1name", otherArgs[0]);
		config.set("Matrix2name", otherArgs[1]);
		config.setInt("n", Integer.parseInt(otherArgs[2]));
		config.setInt("l", Integer.parseInt(otherArgs[3]));
		config.setInt("m", Integer.parseInt(otherArgs[4]));

		job.setJarByClass(MatrixMulti.class);
		job.setMapperClass(MMMapper.class);
		job.setReducerClass(MMReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setNumReduceTasks(2);

		FileInputFormat.addInputPath(job, new Path(otherArgs[5]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
