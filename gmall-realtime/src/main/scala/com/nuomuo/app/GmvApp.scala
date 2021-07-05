package com.nuomuo.app

import com.alibaba.fastjson.JSON
import com.nuomuo.GmallConstants
import com.nuomuo.bean.OrderInfo
import com.nuomuo.utils.MyKafkaUtil
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.phoenix.spark._
import org.apache.spark.rdd.RDD

object GmvApp {
  def main(args: Array[String]): Unit = {
    //1.创建sparkConf
    val sparkConf: SparkConf = new SparkConf().setAppName("GmvApp").setMaster("local[*]")

    //2.创建StreamingContext
    val ssc: StreamingContext = new StreamingContext(sparkConf, Seconds(5))

    //3.获取kafka中的数据
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = MyKafkaUtil.getKafkaStream(GmallConstants.KAFKA_TOPIC_ORDER, ssc)

    //4.将JSON数据转换为样例类
    val orderInfoDStream: DStream[OrderInfo] = kafkaDStream.mapPartitions((partition: Iterator[ConsumerRecord[String, String]]) => {
      partition.map((record: ConsumerRecord[String, String]) => {
        val orderInfo: OrderInfo = JSON.parseObject(record.value(), classOf[OrderInfo])

        println(orderInfo)
        orderInfo.create_date = orderInfo.create_time.split(" ")(0)

        orderInfo.create_hour = orderInfo.create_time.split(" ")(1).split(":")(0)

        orderInfo
      })
    })

    //5.将数据写入Hbase
    orderInfoDStream.foreachRDD((rdd: RDD[OrderInfo]) =>{
      rdd.saveToPhoenix("GMALL2021_ORDER_INFO",
        Seq("ID", "PROVINCE_ID", "CONSIGNEE", "ORDER_COMMENT", "CONSIGNEE_TEL", "ORDER_STATUS", "PAYMENT_WAY", "USER_ID", "IMG_URL", "TOTAL_AMOUNT", "EXPIRE_TIME", "DELIVERY_ADDRESS", "CREATE_TIME", "OPERATE_TIME", "TRACKING_NO", "PARENT_ORDER_ID", "OUT_TRADE_NO", "TRADE_BODY", "CREATE_DATE", "CREATE_HOUR"),
        HBaseConfiguration.create(),
        Some("node1,node2,node3:2181"))
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
