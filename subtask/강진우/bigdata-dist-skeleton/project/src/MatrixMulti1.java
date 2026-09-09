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

public class MatrixMulti1 {

	// =========================================================================
	// 1-Phase Mapper: 원소 하나를 필요한 모든 (i, j) Reducer로 복제 방출
	// =========================================================================
	public static class MMMapper extends Mapper<Object, Text, Text, Text> {
		private Text keypair = new Text();
		private Text valpair = new Text();
		private String Matrix1name;
		private String Matrix2name;
		private int n; // Matrix A의 행 수
		private int l; // Matrix A의 열 수 (= Matrix B의 행 수)
		private int m; // Matrix B의 열 수

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			Matrix1name = config.get("Matrix1name");
			Matrix2name = config.get("Matrix2name");
			n = config.getInt("n", 2);
			l = config.getInt("l", 2);
			m = config.getInt("m", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "A\t0\t0\t4" 또는 "B\t0\t1\t2"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length < 4) return;

			String mat = tokens[0];
			int row = Integer.parseInt(tokens[1]);
			int col = Integer.parseInt(tokens[2]);
			String val = tokens[3];

			if (mat.equals(Matrix1name)) {
				// A(i, k, val)은 결과 C의 i행에 있는 모든 j (0 ~ m-1) 계산에 쓰임!
				// 따라서 (i, 0), (i, 1), ... (i, m-1) 방으로 복제 전송
				for (int j = 0; j < m; j++) {
					keypair.set(row + "\t" + j);              // Key: (i, j)
					valpair.set(Matrix1name + "\t" + col + "\t" + val); // Val: "A\tk\tval"
					context.write(keypair, valpair);
				}
			} else if (mat.equals(Matrix2name)) {
				// B(k, j, val)은 결과 C의 j열에 있는 모든 i (0 ~ n-1) 계산에 쓰임!
				// 따라서 (0, j), (1, j), ... (n-1, j) 방으로 복제 전송
				for (int i = 0; i < n; i++) {
					keypair.set(i + "\t" + col);              // Key: (i, j)
					valpair.set(Matrix2name + "\t" + row + "\t" + val); // Val: "B\tk\tval"
					context.write(keypair, valpair);
				}
			}
		}
	}

	// =========================================================================
	// 1-Phase Reducer: (i, j) 좌표 방에서 A의 i행 벡터와 B의 j열 벡터의 내적(Dot Product) 계산
	// =========================================================================
	public static class MMReducer extends Reducer<Text, Text, Text, IntWritable> {
		private IntWritable val = new IntWritable();
		private String Matrix1name;
		private String Matrix2name;
		private int l; // 공통 차원 크기

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
			// key: (i, j) 좌표
			// values: ["A\t0\t4", "A\t1\t3", "B\t0\t-1", "B\t1\t6", ...]

			int[] a = new int[l];
			int[] b = new int[l];

			for (Text valText : values) {
				String[] tokens = valText.toString().split("\\s+");
				String mat = tokens[0];
				int k = Integer.parseInt(tokens[1]);
				int elementVal = Integer.parseInt(tokens[2]);

				if (mat.equals(Matrix1name)) {
					a[k] = elementVal;
				} else if (mat.equals(Matrix2name)) {
					b[k] = elementVal;
				}
			}

			// 벡터 내적(Dot Product): C(i, j) = sum( A[k] * B[k] )
			int sum = 0;
			for (int k = 0; k < l; k++) {
				sum += a[k] * b[k];
			}

			val.set(sum);
			context.write(key, val); // yield ("i\tj", C(i, j))
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 7) {
			System.err.println("Usage: MatrixMulti1 <Matrix 1 name> <Matrix 2 name> <n> <l> <m> <in> <out>");
			System.exit(2);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[6]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "1-phase matrix multiplication");
		Configuration config = job.getConfiguration();
		config.set("Matrix1name", otherArgs[0]);
		config.set("Matrix2name", otherArgs[1]);
		config.setInt("n", Integer.parseInt(otherArgs[2]));
		config.setInt("l", Integer.parseInt(otherArgs[3]));
		config.setInt("m", Integer.parseInt(otherArgs[4]));

		job.setJarByClass(MatrixMulti1.class);
		job.setMapperClass(MMMapper.class);
		job.setReducerClass(MMReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setNumReduceTasks(2);

		FileInputFormat.addInputPath(job, new Path(otherArgs[5]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
