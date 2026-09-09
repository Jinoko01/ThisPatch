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

public class AllPairPartitionSelf {

	// =========================================================================
	// Mapper: 자기 자신과의 대칭 2D 격자 분할 (삼각형 상삼각 영역만 연산하여 중복 50% 절감)
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private String Tablename;
		private int numberOfPartitions = 2; // k
		private Text emitkey = new Text();
		private Text emitval = new Text();
		private Random rn = new Random();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Tablename = configuration.get("Tablename");
			numberOfPartitions = configuration.getInt("numberOfPartitions", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tuple = line.split("\\s+");
			if (tuple.length <= 1) return;

			int partitionId = rn.nextInt(numberOfPartitions);

			// 1. 행(Row) 역할로 (partitionId, j) 방으로 복제 (j >= partitionId)
			for (int j = partitionId; j < numberOfPartitions; j++) {
				emitkey.set(partitionId + "," + j);
				if (j == partitionId) {
					emitval.set("DIAG\t" + line); // 대각선 방
				} else {
					emitval.set("ROW\t" + line);
				}
				context.write(emitkey, emitval);
			}

			// 2. 열(Col) 역할로 (i, partitionId) 방으로 복제 (i < partitionId)
			for (int i = 0; i < partitionId; i++) {
				emitkey.set(i + "," + partitionId);
				emitval.set("COL\t" + line);
				context.write(emitkey, emitval);
			}
		}
	}

	// =========================================================================
	// Reducer: 대칭 격자 방에서 자기 자신과의 전수 비교 수행 (중복 및 자기자신 제거)
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			ArrayList<String> rowList = new ArrayList<>();
			ArrayList<String> colList = new ArrayList<>();
			ArrayList<String> diagList = new ArrayList<>();

			for (Text val : values) {
				String str = val.toString();
				int firstTab = str.indexOf('\t');
				if (firstTab == -1) continue;

				String tag = str.substring(0, firstTab);
				String record = str.substring(firstTab + 1);

				if (tag.equals("ROW")) {
					rowList.add(record);
				} else if (tag.equals("COL")) {
					colList.add(record);
				} else if (tag.equals("DIAG")) {
					diagList.add(record);
				}
			}

			// 1. 비대각선 방 (i != j): Row 그룹과 Col 그룹 1:1 결합
			for (String r1 : rowList) {
				for (String r2 : colList) {
					context.write(new Text(r1), new Text(r2));
				}
			}

			// 2. 대각선 방 (i == j): 같은 파티션 내에서 a < b 인 쌍만 결합 (자기자신 및 중복쌍 제거)
			for (int a = 0; a < diagList.size(); a++) {
				for (int b = a + 1; b < diagList.size(); b++) {
					context.write(new Text(diagList.get(a)), new Text(diagList.get(b)));
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

		if (otherArgs.length != 4) {
			System.out.println("usage: AllPairPartitionSelf <Tablename> <numberOfPartition> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[3]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "allpair-partition-self");
		Configuration config = job.getConfiguration();
		config.set("Tablename", otherArgs[0]);
		config.setInt("numberOfPartitions", Integer.parseInt(otherArgs[1]));

		int k = Integer.parseInt(otherArgs[1]);
		job.setJarByClass(AllPairPartitionSelf.class);
		job.setNumReduceTasks(k * (k + 1) / 2); // 상삼각 격자 방 개수만 할당
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job, new Path(otherArgs[2]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
