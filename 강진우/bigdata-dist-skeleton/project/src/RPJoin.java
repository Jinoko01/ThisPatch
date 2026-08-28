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

public class RPJoin {

	// =========================================================================
	// Mapper: 테이블명에 따라 지정된 조인 컬럼(JoinAttr) 값을 추출하여 Key로 방출
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private String Table1name;
		private String Table2name;
		private int JoinAttr1 = 1; // Table 1의 조인 컬럼 인덱스
		private int JoinAttr2 = 1; // Table 2의 조인 컬럼 인덱스

		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
			JoinAttr1 = configuration.getInt("JoinAttr1", 1);
			JoinAttr2 = configuration.getInt("JoinAttr2", 1);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "r\t1\t5\t11" 또는 "s\t1\t15\t11"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length <= 1) return;

			String tableName = tokens[0];
			String joinKeyVal = "";

			if (tableName.equals(Table1name) && tokens.length > JoinAttr1) {
				joinKeyVal = tokens[JoinAttr1];
			} else if (tableName.equals(Table2name) && tokens.length > JoinAttr2) {
				joinKeyVal = tokens[JoinAttr2];
			} else {
				return;
			}

			emitkey.set(joinKeyVal); // Key: 조인 키 값 (예: "11")
			emitval.set(line);       // Value: 레코드 전체 라인 ("r\t1\t5\t11")
			context.write(emitkey, emitval);
		}
	}

	// =========================================================================
	// Reducer: 동일한 조인 키 방에 모인 R 테이블 레코드와 S 테이블 레코드를 1:1 매칭 결합
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private ArrayList<String> r;
		private ArrayList<String> s;
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
			r = new ArrayList<String>();
			s = new ArrayList<String>();

			// 1. 같은 조인 키 방에 도착한 레코드들을 R 목록과 S 목록으로 분리
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

			// 2. R과 S의 이중 루프로 조인된 레코드 쌍 방출
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

		if (otherArgs.length != 6) {
			System.out.println("usage: RPJoin <Table1name> <Table2name> <JoinAttr1> <JoinAttr2> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[5]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		Job job = new Job(conf, "repartition-join");
		Configuration config = job.getConfiguration();
		config.set("Table1name", otherArgs[0]);
		config.set("Table2name", otherArgs[1]);
		config.setInt("JoinAttr1", Integer.parseInt(otherArgs[2]));
		config.setInt("JoinAttr2", Integer.parseInt(otherArgs[3]));

		job.setJarByClass(RPJoin.class);
		job.setNumReduceTasks(2);
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job, new Path(otherArgs[4]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
