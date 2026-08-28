package ssafy;

import java.io.IOException;
import java.util.Random;
import java.lang.Math;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
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

public class KMeans {

	// =========================================================================
	// Mapper: 각 데이터 포인트를 K개의 중심점(Centers) 중 가장 가까운 중심점에 할당
	// =========================================================================
	public static class MapperClass extends Mapper<Object, Text, IntWritable, Text> {
		private int dimension = 2;
		private IntWritable emitKey = new IntWritable();
		private Text emitval = new Text();
		private int K;
		private double[][] centers = null;
		private double[] point = null;

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			K = config.getInt("K", 2);
			dimension = config.getInt("dimension", 2);
			centers = new double[K][dimension];
			point = new double[dimension];

			// Broadcast된 K개의 클러스터 중심점 파라미터 읽기
			for (int cid = 0; cid < K; cid++) {
				String strCenter = config.get("strCenters." + cid);
				if (strCenter != null) {
					stringToDoubleArray(strCenter, dimension, centers[cid]);
				}
			}
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String line = value.toString().trim();
			if (line.isEmpty()) return;

			if (!stringToDoubleArray(line, dimension, point)) return;

			int which_center = 0;
			double min_dist = Double.MAX_VALUE;

			// K개의 중심점 중 유클리드 제곱 거리가 가장 가까운 중심점 탐색
			for (int cid = 0; cid < K; cid++) {
				double d = computeDistance(point, centers[cid], dimension);
				if (d < min_dist) {
					min_dist = d;
					which_center = cid;
				}
			}

			// Value: 좌표값 + 거리오차제곱 (예: "7.96\t8.13\t0.045")
			emitval.set(doubleArrayToString(point, dimension) + "\t" + min_dist);
			emitKey.set(which_center);
			context.write(emitKey, emitval);
		}
	}

	// =========================================================================
	// Reducer: 각 클러스터에 모인 포인트들의 산술 평균을 계산하여 '새로운 중심점' 갱신
	// =========================================================================
	public static class ReducerClass extends Reducer<IntWritable, Text, IntWritable, Text> {
		private Text result = new Text();
		private int dimension = 2;

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			Configuration config = context.getConfiguration();
			dimension = config.getInt("dimension", 2);
		}

		@Override
		public void reduce(IntWritable key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			double[] sum = new double[dimension + 1];
			double[] point = new double[dimension + 1];
			int count = 0;

			for (Text val : values) {
				if (stringToDoubleArray(val.toString(), dimension + 1, point)) {
					for (int i = 0; i < dimension; i++) {
						sum[i] += point[i];
					}
					sum[dimension] += point[dimension]; // Sum of Squared Distances 누적
					count++;
				}
			}

			if (count > 0) {
				// 차원별 평균 계산 (새로운 군집 중심)
				for (int i = 0; i < dimension; i++) {
					sum[i] /= count;
				}
			}

			result.set(doubleArrayToString(sum, dimension) + "\t" + sum[dimension]);
			context.write(key, result);
		}
	}

	// 유클리드 거리의 제곱 계산
	public static double computeDistance(double[] arr1, double[] arr2, int d) {
		if (arr1.length < d || arr2.length < d) return -1;
		double sum = 0;
		for (int i = 0; i < d; i++) {
			sum += (arr1[i] - arr2[i]) * (arr1[i] - arr2[i]);
		}
		return sum;
	}

	// double 배열을 탭(\t) 구분 문자열로 변환
	public static String doubleArrayToString(double[] arr, int d) {
		StringBuilder sb = new StringBuilder();
		if (d == 0) return "";
		sb.append(arr[0]);
		for (int i = 1; i < d; i++) {
			sb.append("\t").append(arr[i]);
		}
		return sb.toString();
	}

	// 탭 구분 문자열을 double 배열로 변환
	public static boolean stringToDoubleArray(String str, int d, double[] arr) {
		if (arr.length < d) return false;
		String[] strarr = str.split("\\s+");
		for (int i = 0; i < strarr.length && i < d; i++) {
			arr[i] = Double.parseDouble(strarr[i]);
		}
		return true;
	}

	// Center ID에 해당하는 HDFS 파트 파일명 반환
	public static String getFilename(int cid) {
		return String.format("/part-r-%05d", cid);
	}

	// =========================================================================
	// Main: 수렴할 때까지 MapReduce 반복(Iterative) 실행
	// =========================================================================
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length != 7) {
			System.err.println("Usage: KMeans <seed> <dimension> <K> <maxIter> <epsilon> <in> <out>");
			System.exit(2);
		}

		long seed = Long.parseLong(otherArgs[0]);
		int dimension = Integer.parseInt(otherArgs[1]);
		int K = Integer.parseInt(otherArgs[2]);
		int maxIter = Integer.parseInt(otherArgs[3]);
		double epsilon = Double.parseDouble(otherArgs[4]);

		if (maxIter < 5) {
			System.err.println("<MaxIter> should be at least 5!");
			System.exit(2);
		}

		double[] center = new double[dimension];
		Random rand = new Random(seed);

		conf.setInt("dimension", dimension);
		conf.setInt("K", K);

		// 초기 중심점 랜덤 설정 (Broadcast)
		for (int cid = 0; cid < K; ++cid) {
			String name = "strCenters." + cid;
			String value = "" + (rand.nextFloat() * 10);
			for (int dim = 1; dim < dimension; ++dim) {
				value += "\t" + (rand.nextFloat() * 10);
			}
			conf.set(name, value);
		}

		double prevSSD = 10000000, newSSD = 0;
		int itr = 0;
		int iterationFlag = 1;

		while ((iterationFlag == 1) && (itr < maxIter)) {
			Job job = new Job(conf, "k-means clustering iteration " + itr);
			job.setJarByClass(KMeans.class);
			job.setMapperClass(MapperClass.class);
			job.setReducerClass(ReducerClass.class);
			job.setOutputKeyClass(IntWritable.class);
			job.setOutputValueClass(Text.class);
			job.setNumReduceTasks(K);

			String outputdirectory = otherArgs[6] + itr;
			int tmpnum = itr - 2;
			String deletedirectory = otherArgs[6] + tmpnum;
			if (itr >= 2) {
				Path deletedir = new Path(deletedirectory);
				if (FileSystem.get(conf).exists(deletedir)) {
					FileSystem.get(conf).delete(deletedir, true);
				}
			}

			Path outdir = new Path(outputdirectory);
			if (FileSystem.get(conf).exists(outdir)) {
				FileSystem.get(conf).delete(outdir, true);
			}

			FileInputFormat.addInputPath(job, new Path(otherArgs[5]));
			FileOutputFormat.setOutputPath(job, outdir);
			job.waitForCompletion(true);

			newSSD = 0;
			FileSystem fs = outdir.getFileSystem(conf);
			for (int cid = 0; cid < K; cid++) {
				Path partFile = new Path(outdir + getFilename(cid));
				if (fs.exists(partFile)) {
					FSDataInputStream fp = fs.open(partFile);
					String line = null;
					while ((line = fp.readLine()) != null) {
						String[] arr = line.split("\\s+");
						if (arr.length <= dimension) continue;
						int centerId = Integer.parseInt(arr[0]);
						for (int i = 1; i <= dimension; i++) {
							center[i - 1] = Double.parseDouble(arr[i]);
						}
						conf.set("strCenters." + centerId, doubleArrayToString(center, dimension));
						newSSD += Double.parseDouble(arr[dimension + 1]);
					}
					fp.close();
				}
			}
			newSSD = Math.sqrt(newSSD);

			if ((itr >= 5) && (Math.abs(prevSSD - newSSD) / Math.abs(newSSD)) <= epsilon) {
				iterationFlag = 0;
				System.out.println("ErrorRatio: " + (Math.abs(prevSSD - newSSD) / Math.abs(newSSD)));
			}
			prevSSD = newSSD;
			itr++;
		}

		System.out.println("Number of Iterations: " + (itr));
		String resultdir = otherArgs[6] + (itr - 1);
		System.out.println("Final cluster centers saved in HDFS directory: " + resultdir);
	}
}
