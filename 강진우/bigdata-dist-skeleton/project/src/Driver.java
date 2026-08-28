package ssafy;

import org.apache.hadoop.util.ProgramDriver;

public class Driver {
	public static void main(String[] args) {
		int exitCode = -1;
		ProgramDriver pgd = new ProgramDriver();
		try {
			pgd.addClass("wordcount", Wordcount.class, "A map/reduce program that performs word counting.");
			pgd.addClass("labelratio", LabelRatio.class, "A 2-phase map/reduce program that computes word label ratios.");
			pgd.addClass("matadd", MatrixAdd.class, "A map/reduce program that performs matrix addition.");
			pgd.addClass("matmulti1", MatrixMulti1.class, "A 1-phase map/reduce program that performs matrix multiplication.");
			pgd.addClass("matmulti2", MatrixMulti2.class, "A 2-phase map/reduce program that performs matrix multiplication.");
			pgd.addClass("matmulti3", MatrixMulti3.class, "A 1-phase matrix multiplication with secondary sort streaming.");
			pgd.addClass("matmulti", MatrixMulti.class, "A standard 1-phase matrix multiplication.");
			pgd.addClass("rpjoin", RPJoin.class, "A reduce-side repartition join program.");
			pgd.addClass("rpjoinsort", RPJoinSort.class, "A reduce-side repartition join program with secondary sort.");
			pgd.addClass("allpairjoin", AllPairJoin.class, "An all-pair join program with 1D partition replication.");
			pgd.addClass("allpairpartition", AllPairPartition.class, "An all-pair 2D cross-partition join program.");
			pgd.addClass("allpairpartitionself", AllPairPartitionSelf.class, "A symmetric 2D self all-pair join program.");
			pgd.addClass("knnjoin", KNNJoin.class, "A 2-phase all-pair K-nearest neighbors join program.");
			pgd.addClass("kmeans", KMeans.class, "An iterative K-means clustering program.");
			pgd.addClass("topksearch", TopKSearch.class, "A 2-phase distributed Top-K search program.");
			pgd.addClass("commonitemcount", CommonItemCount.class, "A 2-phase inverted-index common item count program.");
			pgd.addClass("setsimjoin", SetSimJoin.class, "A 2-phase Jaccard set similarity join program.");
			pgd.addClass("vecsimjoin", VecSimJoin.class, "A distributed vector similarity range join program.");
			pgd.addClass("vecsimtopkjoin", VecSimTopKJoin.class, "A 2-phase distributed vector similarity Top-K join program.");

			pgd.driver(args);
			exitCode = 0;
		}
		catch(Throwable e) {
			e.printStackTrace();
		}

		System.exit(exitCode);
	}
}
