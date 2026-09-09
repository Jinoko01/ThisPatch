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

public class AllPairJoin {

	// =========================================================================
	// Mapper: 1D All-Pair 분할
	// Table1(R)은 1개의 랜덤 파티션 방으로만 가고,
	// Table2(S)는 모든 파티션 방으로 복제(Broadcast)되어 전수 비교 준비!
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private String Table1name;
		private String Table2name;
		private int JoinAttr1 = 1;
		private int JoinAttr2 = 1;
		private int numberOfPartition = 2;

		private Text emitkey = new Text();
		private Text emitval = new Text();
		private Random rn = new Random();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
			JoinAttr1 = configuration.getInt("JoinAttr1", 1);
			JoinAttr2 = configuration.getInt("JoinAttr2", 1);
			numberOfPartition = configuration.getInt("NumPartition", 2);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tuple = line.split("\\s+");
			if (tuple.length <= 1) return;

			String tableName = tuple[0];
			emitval.set(line);

			if (tableName.equals(Table1name)) {
				// R 레코드는 p개 파티션 중 1개의 방에만 랜덤 배정
				int partitionId = rn.nextInt(numberOfPartition);
				emitkey.set(Integer.toString(partitionId));
				context.write(emitkey, emitval);
			} else if (tableName.equals(Table2name)) {
				// S 레코드는 R과 1:1로 전부 만나야 하므로 모든 방(0 ~ p-1)으로 복제 전송!
				for (int i = 0; i < numberOfPartition; i++) {
					emitkey.set(Integer.toString(i));
					context.write(emitkey, emitval);
				}
			}
		}
	}

	// =========================================================================
	// Reducer: 각 파티션 방에서 R의 부분집합과 S 전체를 전수 비교(Cartesian Product)하여 조인
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private ArrayList<String> r;
		private ArrayList<String> s;
		private String Table1name;
		private String Table2name;
		private int JoinAttr1 = 0;
		private int JoinAttr2 = 0;

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
			JoinAttr1 = configuration.getInt("JoinAttr1", 1);
			JoinAttr2 = configuration.getInt("JoinAttr2", 1);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			r = new ArrayList<String>();
			s = new ArrayList<String>();

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

			// 방 안에서 R과 S를 1:1 전수 비교하여 조인 조건(JoinAttr1 == JoinAttr2) 검사
			for (String rRec : r) {
				String[] rTokens = rRec.split("\\s+");
				if (rTokens.length <= JoinAttr1) continue;

				for (String sRec : s) {
					String[] sTokens = sRec.split("\\s+");
					if (sTokens.length <= JoinAttr2) continue;

					if (rTokens[JoinAttr1].equals(sTokens[JoinAttr2])) {
						context.write(new Text(rRec), new Text(sRec));
					}
				}
			}

			r.clear();
			s.clear();
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 7) {
			System.out.println("usage: AllPairJoin <Table1name> <Table2name> <JoinAttr1> <JoinAttr2> <NumPartition> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[6]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "all-pair-join");
		Configuration config = job.getConfiguration();
		config.set("Table1name", otherArgs[0]);
		config.set("Table2name", otherArgs[1]);
		config.setInt("JoinAttr1", Integer.parseInt(otherArgs[2]));
		config.setInt("JoinAttr2", Integer.parseInt(otherArgs[3]));
		config.setInt("NumPartition", Integer.parseInt(otherArgs[4]));

		job.setJarByClass(AllPairJoin.class);
		job.setNumReduceTasks(Integer.parseInt(otherArgs[4]));
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job, new Path(otherArgs[5]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
