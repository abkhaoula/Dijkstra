 import scala.collection.mutable.Map
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Terminated}
//import akka.actor.SupervisorStrategy.Restart
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout




object Messages {
  case class DoubleLink(nodeRef: ActorRef, distance : Int)
  case class SingleLink(nodeRef: ActorRef, distance : Int)
  case class Visit(distance : Int, prev : List[ActorRef])
  case class Visited(queue : Map[ActorRef,Int])
  case object Success
  case object Done
  case object Nothing
  case object Start
  case object Path
  case object Dist
}




class Node extends Actor {
  import Messages._
  implicit val to = Timeout(10 seconds)

  // Map[ActorRef, Distance]
  var edge:Map[ActorRef,Int] = Map()
  var path = List[ActorRef]()
  var visited = 0
  var dist = 10000

  override def receive : Receive = {
    case DoubleLink(nodeRef: ActorRef, distance : Int) =>
      edge = edge + (nodeRef -> distance)
      var future = (nodeRef ? SingleLink(self, distance))
      var r = Await.result(future, 1 second)
      sender ! r
    case SingleLink(nodeRef: ActorRef, distance : Int) =>
      edge = edge + (nodeRef -> distance)
      sender ! Success
    case Start =>
      dist = 0
      self ! Visited(Map[ActorRef,Int]())
    case Visited(queue : Map[ActorRef,Int]) =>
      var q = queue
      for ((k,v) <- edge) 
      {
        var future = (k ? Visit(dist + v, path ::: List[ActorRef](self)))
        var r = Await.result(future, 1 second)
        if (r == Done)
          q = q + (k -> (dist + v))
      }
      visited = 1
      if (! q.isEmpty)
      {
        q.toSeq.sortBy(_._2).head._1 ! Visited(q - q.toSeq.sortBy(_._2).head._1)
      }
    case Visit(distance : Int, prev : List[ActorRef]) =>
      if ((visited == 0) && (distance <= dist))
      {
        dist = distance
        path = prev
        sender ! Done
      }
      else
      {
        sender ! Nothing
      }
    case Path =>
      println("The shortest path is : " + path)
      println("The distance of the shortest path is : " + dist)
    case Dist =>
      sender ! dist
  }
}




object GraphSystem extends App {
  import Messages._
  implicit val to = Timeout(10 seconds)
  val as = ActorSystem("Graph")




  def Dijkstra(N : Int, C : Int, Graph : Map[Int, Map[Int, Int]], R : Map[Int, (Int, Int, List[Int])]) : Map[Int, (Int, Int, List[Int])] ={
    var Current = C
    var queue : Map[Int, Int] = Map()
    var counter = 0
    var Result = R
    while(counter != 6)
    {
      for ((k,v) <- Graph(Current)) 
      {
        if(Result(k)._1 == 0)
        {
          if (Result(k)._2 > (v + Result(Current)._2))
          {
            Result = Result - k
            var distance : Int = v + Result(Current)._2
            var path : List[Int]= Result(Current)._3 ++ List(Current)
            Result += (k -> (0, distance , path))
          }
          if (queue.contains(k))
          {
            queue = queue - k
          }
          queue += (k-> Result(k)._2)
        }
      }
      var tmp = Result(Current)
      Result = Result - Current
      Result += (Current -> (1, tmp._2, tmp._3))
      if (queue.contains(Current))
      {
        queue = queue - Current
      }
      if (! queue.isEmpty)
      {
        Current = queue.toSeq.sortBy(_._2).head._1
      }
      counter += 1
    }
    return (Result)
  }






  // Creating a random graph Graph [nodeId, listOfConnections:[(nodeId -> weight)]]
  val rand = new scala.util.Random
  var N = 10
  var randGraph : Map[Int, Map[Int, Int]] = Map()
  var Result1:Map[Int, (Int, Int, List[Int])] = Map()
  var ActorNodes = List[ActorRef]()
  for (i <- 1 to N)
  {
    var edges : Map[Int, Int] = Map()
    var uniqRand = scala.util.Random.shuffle(1 to N)
    for (j <- 1 to (1 + rand.nextInt(N)))
    {
      if (i != j) edges += (j->rand.nextInt(100))
    }
    randGraph += (i-> edges)
    if (i == 1)
    {
      Result1 += (i-> (0,0,List()))
    }
    else
    {
      Result1 += (i -> (0,10000,List()))
    }
    ActorNodes = ActorNodes :+ as.actorOf(Props[Node], ("0" + i.toString))
  }

  //Creating the same graph with actors
  for ((k,v) <- randGraph)
  {
    for ((kk,vv) <- v)
    {
      var future = ActorNodes(k-1) ? SingleLink(ActorNodes(kk-1), vv)
      r = Await.result(future, 1 second)
      if (r != Success)
        println("ERROR")
    }
  }





  Result1 = Dijkstra(N, 1, randGraph, Result1)

  ActorNodes(0) ! Start
  Thread.sleep(100)

  for (i <- 1 to 10)
  {
    var future = (ActorNodes(i-1) ?  Dist)
    var r = Await.result(future, 1 second)
    assert(r == Result1(i)._2)
  }





  // Tring with a specific example

  var Graph:Map[Int, Map[Int, Int]] = Map()
  Graph += (1-> Map(2->2, 3->4))
  Graph += (2-> Map(1->2, 3->1, 4->4, 5->2))
  Graph += (3-> Map(1->4, 2->1, 5->3))
  Graph += (4-> Map(2->4, 5->3, 6->2))
  Graph += (5-> Map(2->2, 3->3, 4->3, 6->2))
  Graph += (6-> Map(4->2, 5->4))

  // Creating the result variable [nodeId, (isvisited, distance, path)]
  var Result:Map[Int, (Int, Int, List[Int])] = Map()
  Result += (1-> (0,0,List()))
  Result += (2-> (0,10000,List()))
  Result += (3-> (0,10000,List()))
  Result += (4-> (0,10000,List()))
  Result += (5-> (0,10000,List()))
  Result += (6-> (0,10000,List()))


  // Creating the Same Graph nodes
  val Node1 = as.actorOf(Props[Node], "1")
  val Node2 = as.actorOf(Props[Node], "2")
  val Node3 = as.actorOf(Props[Node], "3")
  val Node4 = as.actorOf(Props[Node], "4")
  val Node5 = as.actorOf(Props[Node], "5")
  val Node6 = as.actorOf(Props[Node], "6")

  // Creating the Same edges
  var future = (Node1 ? DoubleLink(Node2, 2 ))
  var r = Await.result(future, 1 second)
  if (r == Success)
    println("Node1 --2-- Node2")
  future = (Node1 ? DoubleLink(Node3, 4))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node1 --4-- Node3")
  future = (Node2 ? DoubleLink(Node3, 1))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node2 --1-- Node3")
  future = (Node2 ? DoubleLink(Node4, 4))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node2 --4-- NodeD")
  future = (Node2 ? DoubleLink(Node5, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node2 --2-- Node5")
  future = (Node3 ? DoubleLink(Node5, 3))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node3 --3-- Node5")
  future = (Node4 ? DoubleLink(Node5, 3))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node4 --3-- Node5")
  future = (Node4 ? DoubleLink(Node6, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node4 --2-- Node6")
  future = (Node5 ? DoubleLink(Node6, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("Node5 --2-- Node6")
  
  //Dijkstra sequential
  Result = Dijkstra(6, 1, Graph, Result)

  
  //Dijkstra with Actors
  Node1 ! Start
  Thread.sleep(100)
  //Node6 ! Path
  //Node6 ! Dist


  //assert
  future = (Node1 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(1)._2)
  future = (Node2 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(2)._2)
  future = (Node3 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(3)._2)
  future = (Node4 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(4)._2)
  future = (Node5 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(5)._2)
  future = (Node6 ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result(6)._2)
}
