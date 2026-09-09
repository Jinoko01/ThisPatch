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

public class RPJoinSort {

	// =========================================================================
	// 복합 키 (Composite Key): (조인 키 값, 테이블 태그: R=0, S=1)
	// =========================================================================
	public static class IntPair implements WritableComparable<IntPair> {
		private int first = 0;  // join attribute 값
		private int second = 0; // table tag (R=0, S=1)

		public void set(int left, int right) {
			first = left;
			second = right;
		}

		public String toString() {
			return Integer.toString(first) + "\t" + Integer.toString(second);
		}

		public int getFirst() { return first; }
		public int getSecond() { return second; }

		@Override
		public void readFields(DataInput in) throws IOException {
			first = in.readInt() + Integer.MIN_VALUE;
			second = in.readInt() + Integer.MIN_VALUE;
		}

		@Override
		public void write(DataOutput out) throws IOException {
			out.writeInt(first - Integer.MIN_VALUE);
			out.writeInt(second - Integer.MIN_VALUE);
		}

		@Override
		public int hashCode() {
			return first * 157 + second;
		}

		@Override
		public boolean equals(Object right) {
			if (right instanceof IntPair) {
				IntPair r = (IntPair) right;
				return r.first == first && r.second == second;
			}
			return false;
		}

		public static class Comparator extends WritableComparator {
			public Comparator() {
				super(IntPair.class);
			}

			@Override
			public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
				return compareBytes(b1, s1, l1, b2, s2, l2);
			}
		}

		static {
			WritableComparator.define(IntPair.class, new Comparator());
		}

		@Override
		public int compareTo(IntPair o) {
			if (first != o.first) {
				return first < o.first ? -1 : 1;
			} else if (second != o.second) {
				return second < o.second ? -1 : 1;
			} else {
				return 0;
			}
		}
	}

	// =========================================================================
	// Grouping Comparator: first(조인 키)만 보고 동일한 Reducer 그룹으로 묶음
	// =========================================================================
	public static class FirstGroupingComparator implements RawComparator<IntPair> {
		@Override
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return WritableComparator.compareBytes(b1, s1, Integer.SIZE / 8,
					b2, s2, Integer.SIZE / 8);
		}

		@Override
		public int compare(IntPair o1, IntPair o2) {
			int l = o1.getFirst();
			int r = o2.getFirst();
			return l == r ? 0 : (l < r ? -1 : 1);
		}
	}

	// =========================================================================
	// Partitioner: first(조인 키)를 기준으로 Reducer 노드 결정
	// =========================================================================
	public static class FirstPartitioner extends Partitioner<IntPair, Text> {
		@Override
		public int getPartition(IntPair key, Text value, int numPartitions) {
			return Math.abs(key.getFirst() * 127) % numPartitions;
		}
	}

	// =========================================================================
	// Mapper: (조인키, R=0 또는 S=1) 복합키 방출
	// =========================================================================
	public static class MapClass1 extends Mapper<Object, Text, IntPair, Text> {
		private int joinatt = 0; // join attribute column index
		private IntPair emitkey = new IntPair();

		@Override
		public void setup(Context context) throws IOException {
			Configuration configuration = context.getConfiguration();
			joinatt = configuration.getInt("joinatt", 3);
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			// 입력 라인 예시: "r\t1\t5\t11" 또는 "s\t1\t15\t11"
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			String[] tokens = line.split("\\s+");
			if (tokens.length <= joinatt) return;

			String table = tokens[0];
			int joinVal = Integer.parseInt(tokens[joinatt]);
			int tableTag = table.equalsIgnoreCase("r") ? 0 : 1; // R=0 (우선순위 높음), S=1

			emitkey.set(joinVal, tableTag);
			context.write(emitkey, value);
		}
	}

	// =========================================================================
	// Reducer: Secondary Sort 덕분에 R 레코드가 무조건 먼저 들어오고,
	// 이후 들어오는 S 레코드는 메모리에 담지 않고 들어오는 즉시 R과 결합하여 방출!
	// =========================================================================
	public static class ReduceClass1 extends Reducer<IntPair, Text, Text, Text> {
		private ArrayList<String> r;

		@Override
		public void reduce(IntPair key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			r = new ArrayList<String>();

			for (Text val : values) {
				String record = val.toString();
				String[] tokens = record.split("\\s+");
				if (tokens.length == 0) continue;

				if (tokens[0].equalsIgnoreCase("r")) {
					// R 레코드는 먼저 모아둠
					r.add(record);
				} else {
					// S 레코드는 메모리에 쌓지 않고, 들어오는 즉시 기존 R 목록과 매칭하여 스트리밍 출력!
					for (String rRec : r) {
						context.write(new Text(rRec), new Text(record));
					}
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

		if (otherArgs.length != 3) {
			System.out.println("usage: RPJoinSort <joinatt> <in> <out>");
			System.exit(1);
		}

		FileSystem hdfs = FileSystem.get(conf);
		Path output = new Path(otherArgs[2]);
		if (hdfs.exists(output)) {
			hdfs.delete(output, true);
		}

		conf.setInt("joinatt", Integer.parseInt(otherArgs[0]));

		Job job = new Job(conf, "repartition-join-secondary-sort");
		job.setJarByClass(RPJoinSort.class);
		job.setNumReduceTasks(2);
		job.setMapperClass(MapClass1.class);
		job.setReducerClass(ReduceClass1.class);
		job.setMapOutputKeyClass(IntPair.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		job.setPartitionerClass(FirstPartitioner.class);
		job.setGroupingComparatorClass(FirstGroupingComparator.class);

		FileInputFormat.addInputPath(job, new Path(otherArgs[1]));
		FileOutputFormat.setOutputPath(job, output);
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
