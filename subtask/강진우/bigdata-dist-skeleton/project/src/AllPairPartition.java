package ssafy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

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

public class AllPairPartition {

	// =========================================================================
	// Mapper: 2D 바둑판 격자(k x k) 분할
	// R 테이블은 랜덤 행(row)을 골라 해당 행의 모든 열(0 ~ k-1)로 복제 (복제수 k)
	// S 테이블은 랜덤 열(col)을 골라 해당 열의 모든 행(0 ~ k-1)로 복제 (복제수 k)
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private String Table1name;
		private String Table2name;
		private int numberOfPartitions = 2; // k (격자 한 변의 크기)

		private Text emitkey = new Text();
		private Text emitval = new Text();
		private Random rn = new Random();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
			numberOfPartitions = configuration.getInt("numberOfPartitions", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tuple = line.split("\\s+");
			if (tuple.length <= 1) return;

			int partitionId = rn.nextInt(numberOfPartitions);
			emitval.set(line);

			if (tuple[0].equals(Table1name)) {
				// R 레코드: row=partitionId 고정, 모든 col(0 ~ k-1) 방으로 복제 전송
				for (int j = 0; j < numberOfPartitions; j++) {
					emitkey.set(partitionId + "," + j);
					context.write(emitkey, emitval);
				}
			} else if (tuple[0].equals(Table2name)) {
				// S 레코드: col=partitionId 고정, 모든 row(0 ~ k-1) 방으로 복제 전송
				for (int i = 0; i < numberOfPartitions; i++) {
					emitkey.set(i + "," + partitionId);
					context.write(emitkey, emitval);
				}
			}
		}
	}

	// =========================================================================
	// Reducer: 각 격자 방 (i, j) 안에서 R과 S의 카테시안 곱(전수 비교) 수행
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private String Table1name;
		private String Table2name;

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			ArrayList<String> r = new ArrayList<>();
			ArrayList<String> s = new ArrayList<>();

			for (Text val : values) {
				String record = val.toString();
				String[] tokens = record.split("\\s+");
				if (tokens.length == 0) continue;

				if (tokens[0].equals(Table1name)) {
					r.add(record);
				} else if (tokens[0].equals(Table2name)) {
					s.add(record);
				}
			}

			// 각 방에서 R과 S의 전수 조인 쌍 출력
			for (String rRec : r) {
				for (String sRec : s) {
					context.write(new Text(rRec), new Text(sRec));
				}
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
			System.out.println("usage: AllPairPartition <Table1name> <Table2name> <numberOfPartition> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[4]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "allpair-partition");
		Configuration config = job.getConfiguration();
		config.set("Table1name", otherArgs[0]);
		config.set("Table2name", otherArgs[1]);
		config.setInt("numberOfPartitions", Integer.parseInt(otherArgs[2]));

		int k = Integer.parseInt(otherArgs[2]);
		job.setJarByClass(AllPairPartition.class);
		job.setNumReduceTasks(k * k); // k x k 개수의 Reducer 방 배정
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job, new Path(otherArgs[3]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
