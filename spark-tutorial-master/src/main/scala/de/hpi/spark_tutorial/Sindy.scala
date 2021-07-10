package de.hpi.spark_tutorial

import org.apache.spark.sql.SparkSession

import scala.collection.mutable.ArrayBuffer

object Sindy {

  def discoverINDs(inputs: List[String], spark: SparkSession): Unit = {
    import spark.implicits._

    // Split Input Dataframes in a set of (value, Array(columnName)) tuples
    // Should be solvable by collect set and explode as well, which would probably be faster
    var ds = spark.emptyDataset[(String, Array[String])]
    for (input <- inputs) {
      val csv_file = spark.read.format("csv").option("header", "true").option("delimiter", ";").load(input)
      val columns = csv_file.columns.map(col => Array(col)) // .map(col => Array(input + col))  // Necessary if col names overlap
      val value_column_name_tuples = csv_file.flatMap(row =>  {
        row.toSeq.map(_.toString).zip(columns)
      })
      ds = ds.union(value_column_name_tuples)
    }
    // ds.show()

    val x = ds.rdd
      // pre aggregation by key, each key now has all its occurrence column names
      .reduceByKey((v1: Array[String], v2: Array[String]) => v1.union(v2).distinct)
      //.partitionBy(1)  // We should be able to partition via key, however the index partitioning does not work
      // Just assume that spark does the repartitioning work for us
      // create inclusion lists
      .map(tuple => ArrayBuffer(tuple._2: _*))
      .flatMap(attributeSet => attributeSet.map(entry => (entry, attributeSet - entry)))
      // Here could be an partition again (see above)
      // Aggregate by intersection
      .reduceByKey((v1: ArrayBuffer[String], v2 : ArrayBuffer[String]) => v1.intersect(v2))
      // Filter out empty matches
      .filter(tuple => tuple._2.size > 0)
      // Create output string
      .map(tuple => {
        val delim = ", "
        s"${tuple._1} < ${tuple._2.mkString(delim)}"
      })
      // Sort by output string
      .sortBy(s => s)
      // Print stuff
      .collect()
      .foreach(println)
  }
}
