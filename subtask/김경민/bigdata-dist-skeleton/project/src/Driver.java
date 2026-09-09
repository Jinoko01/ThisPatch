package ssafy;

import org.apache.hadoop.util.ProgramDriver;

public class Driver {
	public static void main(String[] args) {
		int exitCode = -1;
		ProgramDriver pgd = new ProgramDriver();
		try {

			pgd.addClass("wordcount", Wordcount.class, "A map/reduce program that performs word counting.");

			pgd.addClass("wordcount1char", Wordcount1char.class, "A map/reduce program that counts first characters.");

			pgd.addClass("wordcountsort", Wordcountsort.class, "A map/reduce program that partitions words by first character.");

			pgd.addClass("invertedindex", InvertedIndex.class, "A map/reduce program that builds an inverted index.");

			pgd.addClass("matadd", MatrixAdd.class, "A map/reduce program that performs matrix addition.");

			pgd.addClass("matmulti", MatrixMulti.class, "1-Phase Matrix Multiplication PReparation");

			pgd.addClass("allpair", AllPairPartition.class, "A map/reduce program that partitions all pairs of tuples from both tables.");

			pgd.addClass("allpairself", AllPairPartitionSelf.class, "A map/reduce program that partitions all pairs of tuples from a table.");


			pgd.addClass("itemcount", CommonItemCount.class, "A map/reduce program that performs the common item count using the inverted index for a single input file.");

			pgd.addClass("topksearch", TopKSearch.class, "A map/reduce program that performs the top-k search for a single input file.");

      			pgd.driver(args);
			exitCode = 0;
		}
		catch(Throwable e) {
			e.printStackTrace();
		}

		System.exit(exitCode);
	}
}
