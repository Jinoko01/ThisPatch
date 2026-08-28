package ssafy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

public class MatrixMulti2 {

	// =========================================================================
	// Phase 1 Map: 공통 인덱스 k를 Key로 삼아 원소 정보 방출 (복제 없음!)
	// =========================================================================
	public static class M1Mapper extends Mapper<Object, Text, Text, Text> {
		private Text kKey = new Text();
		private Text valInfo = new Text();
		private String Matrix1name;
		private String Matrix2name;

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			Matrix1name = config.get("Matrix1name");
			Matrix2name = config.get("Matrix2name");
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "A\t0\t1\t3" 또는 "B\t1\t0\t6"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length < 4) return;

			String mat = tokens[0];
			String row = tokens[1];
			String col = tokens[2];
			String val = tokens[3];

			if (mat.equals(Matrix1name)) {
				// A(i, k, val): 공통 인덱스는 col(k)
				kKey.set(col); // Key: k
				valInfo.set("A\t" + row + "\t" + val); // Value: "A\ti\tval"
				context.write(kKey, valInfo);
			} else if (mat.equals(Matrix2name)) {
				// B(k, j, val): 공통 인덱스는 row(k)
				kKey.set(row); // Key: k
				valInfo.set("B\t" + col + "\t" + val); // Value: "B\tj\tval"
				context.write(kKey, valInfo);
			}
		}
	}

	// =========================================================================
	// Phase 1 Reduce: 동일한 k를 가진 A의 원소와 B의 원소를 이중 루프로 곱셈
	// =========================================================================
	public static class M1Reducer extends Reducer<Text, Text, Text, IntWritable> {
		private Text coordKey = new Text();
		private IntWritable prodVal = new IntWritable();

		private static class Element {
			int index;
			int value;
			Element(int index, int value) {
				this.index = index;
				this.value = value;
			}
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			// key: k (공통 인덱스)
			List<Element> listA = new ArrayList<>();
			List<Element> listB = new ArrayList<>();

			for (Text valText : values) {
				String[] tokens = valText.toString().split("\\s+");
				String mat = tokens[0];
				int idx = Integer.parseInt(tokens[1]);
				int v = Integer.parseInt(tokens[2]);

				if (mat.equals("A")) {
					listA.add(new Element(idx, v)); // (i, val)
				} else {
					listB.add(new Element(idx, v)); // (j, val)
				}
			}

			// k에 대해 모인 모든 A(i)와 B(j)의 곱을 (i, j) 좌표 Key로 방출
			for (Element a : listA) {
				for (Element b : listB) {
					coordKey.set(a.index + "\t" + b.index); // Key: "i\tj"
					prodVal.set(a.value * b.value);         // Value: A[i,k] * B[k,j]
					context.write(coordKey, prodVal);
				}
			}
		}
	}

	// =========================================================================
	// Phase 2 Map: Phase 1의 출력("i\tj\t곱")을 그대로 읽어 (i, j)를 Key로 방출
	// =========================================================================
	public static class M2Mapper extends Mapper<Object, Text, Text, IntWritable> {
		private Text coordKey = new Text();
		private IntWritable prodVal = new IntWritable();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// Phase 1의 출력 라인 예시: "0\t0\t-4"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length >= 3) {
				coordKey.set(tokens[0] + "\t" + tokens[1]); // Key: "i\tj"
				prodVal.set(Integer.parseInt(tokens[2]));   // Value: product
				context.write(coordKey, prodVal);
			}
		}
	}

	// =========================================================================
	// Phase 2 Reduce: 동일한 (i, j) 좌표의 모든 부분곱들을 합산하여 최종 행렬 C 완성
	// =========================================================================
	public static class M2Reducer extends Reducer<Text, IntWritable, Text, IntWritable> {
		private IntWritable result = new IntWritable();

		@Override
		public void reduce(Text key, Iterable<IntWritable> values, Context context)
				throws IOException, InterruptedException {
			// key: "i\tj", values: [prod_from_k0, prod_from_k1, ...]
			int sum = 0;
			for (IntWritable v : values) {
				sum += v.get();
			}
			result.set(sum);
			context.write(key, result); // yield ("i\tj", sum)
		}
	}

	// =========================================================================
	// Main: Phase 1 -> Phase 2 순차 실행
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 7) {
			System.err.println("Usage: MatrixMulti2 <Matrix 1 name> <Matrix 2 name> <n> <l> <m> <in> <out>");
			System.exit(2);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path finalOutput = new Path(otherArgs[6]);
		Path tmpOutput = new Path("tmp_matmulti2");

		if (hdfs.exists(finalOutput)) {
			hdfs.delete(finalOutput, true);
		}
		if (hdfs.exists(tmpOutput)) {
			hdfs.delete(tmpOutput, true);
		}

		// ---------------------------------------------------------------------
		// Phase 1 Job: k 기준 셔플 & 곱셈
		// ---------------------------------------------------------------------
		Job job1 = new Job(conf, "2-phase matrix multiplication - Phase 1");
		Configuration config1 = job1.getConfiguration();
		config1.set("Matrix1name", otherArgs[0]);
		config1.set("Matrix2name", otherArgs[1]);
		config1.setInt("n", Integer.parseInt(otherArgs[2]));
		config1.setInt("l", Integer.parseInt(otherArgs[3]));
		config1.setInt("m", Integer.parseInt(otherArgs[4]));

		job1.setJarByClass(MatrixMulti2.class);
		job1.setMapperClass(M1Mapper.class);
		job1.setReducerClass(M1Reducer.class);
		job1.setMapOutputKeyClass(Text.class);
		job1.setMapOutputValueClass(Text.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(IntWritable.class);
		job1.setNumReduceTasks(2);

		FileInputFormat.addInputPath(job1, new Path(otherArgs[5]));
		FileOutputFormat.setOutputPath(job1, tmpOutput);
		boolean ok1 = job1.waitForCompletion(true);
		if (!ok1) {
			System.exit(1);
		}

		// ---------------------------------------------------------------------
		// Phase 2 Job: (i, j) 기준 셔플 & 합산
		// ---------------------------------------------------------------------
		Job job2 = new Job(conf, "2-phase matrix multiplication - Phase 2");
		job2.setJarByClass(MatrixMulti2.class);
		job2.setMapperClass(M2Mapper.class);
		job2.setCombinerClass(M2Reducer.class); // 덧셈이므로 Combiner 적용
		job2.setReducerClass(M2Reducer.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(IntWritable.class);
		job2.setNumReduceTasks(2);

		FileInputFormat.addInputPath(job2, tmpOutput);
		FileOutputFormat.setOutputPath(job2, finalOutput);
		boolean ok2 = job2.waitForCompletion(true);
		if (!ok2) {
			System.exit(1);
		}
	}
}
