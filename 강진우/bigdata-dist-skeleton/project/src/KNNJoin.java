package ssafy;

import java.io.IOException;
import java.util.*;

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

public class KNNJoin {

	// =========================================================================
	// Max-Heap 유지를 위한 MyType (거리가 가장 먼 원소가 맨 위에 위치하여 poll()로 제거)
	// =========================================================================
	public static class MyType implements Comparable<MyType> {
		public double dist;
		public String str;

		public MyType(double d, String s) {
			this.dist = d;
			this.str = s;
		}

		@Override
		public int compareTo(MyType o) {
			return this.dist > o.dist ? -1 : (this.dist < o.dist ? 1 : 0);
		}
	}

	// =========================================================================
	// 유클리드 거리(Euclidean Distance) 계산 함수
	// 형식: <Relation id> \tab <record id> \tab <dim 1> \tab <dim 2> ...
	// =========================================================================
	public static double dist(String sp1, String sp2) {
		String[] strarr1 = sp1.split("\\s+");
		String[] strarr2 = sp2.split("\\s+");
		double d1, d2, diff, sum = 0;
		// 0: Relation ID, 1: Record ID -> 2번 인덱스부터 좌표 차원
		for (int i = 2; i < strarr1.length && i < strarr2.length; i++) {
			d1 = Double.parseDouble(strarr1[i]);
			d2 = Double.parseDouble(strarr2[i]);
			diff = d1 - d2;
			sum += diff * diff;
		}
		return Math.sqrt(sum);
	}

	// =========================================================================
	// Phase 1 Mapper: 2D 바둑판 격자 분할 방 배정
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, Text, Text> {
		private String Table1name;
		private String Table2name;
		private int numberOfPartitions = 2; // k
		private Text emitkey = new Text();
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

			if (tuple[0].equals(Table1name)) {
				// Query Point (R): row = partitionId, 모든 col(0 ~ k-1) 방으로 복제
				for (int j = 0; j < numberOfPartitions; j++) {
					emitkey.set(partitionId + "," + j);
					context.write(emitkey, value);
				}
			} else if (tuple[0].equals(Table2name)) {
				// Data Point (S): col = partitionId, 모든 row(0 ~ k-1) 방으로 복제
				for (int i = 0; i < numberOfPartitions; i++) {
					emitkey.set(i + "," + partitionId);
					context.write(emitkey, value);
				}
			}
		}
	}

	// =========================================================================
	// Phase 1 Reducer: 각 격자 방 안에서 R의 각 점마다 로컬 Top-K 최근접 점 탐색
	// =========================================================================
	public static class ReduceClass1 extends Reducer<Text, Text, Text, Text> {
		private String Table1name;
		private String Table2name;
		private int K;
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			Table1name = configuration.get("Table1name");
			Table2name = configuration.get("Table2name");
			K = configuration.getInt("K", 2);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			Vector<String> vecs1 = new Vector<>();
			Vector<String> vecs2 = new Vector<>();

			for (Text val : values) {
				String record = val.toString();
				String[] tokens = record.split("\\s+");
				if (tokens.length == 0) continue;

				if (tokens[0].equals(Table1name)) {
					vecs1.add(record);
				} else if (tokens[0].equals(Table2name)) {
					vecs2.add(record);
				}
			}

			// vecs1의 각 쿼리 점에 대해 vecs2 중에서 가장 가까운 K개 추출
			for (String q : vecs1) {
				PriorityQueue<MyType> pq = new PriorityQueue<>();
				for (String p : vecs2) {
					double d = dist(q, p);
					pq.add(new MyType(d, p));
					if (pq.size() > K) {
						pq.poll();
					}
				}

				while (!pq.isEmpty()) {
					MyType item = pq.poll();
					emitkey.set(q);
					emitval.set(item.str + "\t" + item.dist);
					context.write(emitkey, emitval);
				}
			}
		}
	}

	// =========================================================================
	// Phase 2 Mapper: 쿼리 점(q)을 Key로 삼아 모음
	// =========================================================================
	public static class MapClass2 extends Mapper<Object, Text, Text, Text> {
		private Text emitkey = new Text();
		private Text emitval = new Text();

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "r\t1\t5\t11\ts\t1\t15\t11\t10.0"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length < 9) return;

			// q: tokens[0..3], s: tokens[4..7], dist: tokens[8]
			String q = tokens[0] + "\t" + tokens[1] + "\t" + tokens[2] + "\t" + tokens[3];
			String sAndDist = tokens[4] + "\t" + tokens[5] + "\t" + tokens[6] + "\t" + tokens[7] + "\t" + tokens[8];

			emitkey.set(q);
			emitval.set(sAndDist);
			context.write(emitkey, emitval);
		}
	}

	// =========================================================================
	// Phase 2 Reducer: 각 쿼리 점(q)에 대해 로컬 Top-K 후보들 중 글로벌 최종 Top-K 결정
	// =========================================================================
	public static class ReduceClass2 extends Reducer<Text, Text, Text, Text> {
		private int K;
		private Text emitval = new Text();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			K = configuration.getInt("K", 2);
		}

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
			PriorityQueue<MyType> pq = new PriorityQueue<>();

			for (Text val : values) {
				String str = val.toString();
				String[] tokens = str.split("\\s+");
				if (tokens.length < 5) continue;

				double d = Double.parseDouble(tokens[tokens.length - 1]);
				pq.add(new MyType(d, str));
				if (pq.size() > K) {
					pq.poll();
				}
			}

			// 거리 오름차순 출력을 위해 역순 정렬
			List<MyType> resultList = new ArrayList<>();
			while (!pq.isEmpty()) {
				resultList.add(pq.poll());
			}
			Collections.reverse(resultList);

			for (MyType item : resultList) {
				emitval.set(item.str);
				context.write(key, emitval);
			}
		}
	}

	// =========================================================================
	// Main
	// =========================================================================
	public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();

		if (otherArgs.length != 7) {
			System.out.println("usage: KNNJoin <Table1name> <Table2name> <numberOfPartitions> <K> <in> <out1> <out2>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output1 = new Path(otherArgs[5]);
		Path output2 = new Path(otherArgs[6]);

		if (hdfs.exists(output1)) hdfs.delete(output1, true);
		if (hdfs.exists(output2)) hdfs.delete(output2, true);

		// Phase 1
		Job job1 = new Job(conf, "allpair-knn1");
		Configuration config1 = job1.getConfiguration();
		config1.set("Table1name", otherArgs[0]);
		config1.set("Table2name", otherArgs[1]);
		config1.setInt("numberOfPartitions", Integer.parseInt(otherArgs[2]));
		config1.setInt("K", Integer.parseInt(otherArgs[3]));

		job1.setJarByClass(KNNJoin.class);
		job1.setNumReduceTasks(2);
		job1.setMapperClass(MapClass1.class);
		job1.setReducerClass(ReduceClass1.class);
		job1.setOutputKeyClass(Text.class);
		job1.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job1, new Path(otherArgs[4]));
		FileOutputFormat.setOutputPath(job1, output1);
		if (!job1.waitForCompletion(true)) System.exit(1);

		// Phase 2
		Job job2 = new Job(conf, "allpair-knn2");
		Configuration config2 = job2.getConfiguration();
		config2.setInt("K", Integer.parseInt(otherArgs[3]));

		job2.setJarByClass(KNNJoin.class);
		job2.setNumReduceTasks(2);
		job2.setMapperClass(MapClass2.class);
		job2.setReducerClass(ReduceClass2.class);
		job2.setOutputKeyClass(Text.class);
		job2.setOutputValueClass(Text.class);

		FileInputFormat.addInputPath(job2, output1);
		FileOutputFormat.setOutputPath(job2, output2);
		System.exit(job2.waitForCompletion(true) ? 0 : 1);
	}
}
