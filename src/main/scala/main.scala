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
  
  // Creating the Graph [nodeId, listOfConnections:[(nodeId -> weight)]]
  var Graph:Map[String, Map[String, Int]] = Map()
  Graph += ("A"-> Map("B"->2, "C"->4))
  Graph += ("B"-> Map("A"->2, "C"->1, "D"->4, "E"->2))
  Graph += ("C"-> Map("A"->4, "B"->1, "E"->3))
  Graph += ("D"-> Map("B"->4, "E"->3, "F"->2))
  Graph += ("E"-> Map("B"->2, "C"->3, "D"->3, "F"->2))
  Graph += ("F"-> Map("D"->2, "E"->4))

  // Creating the result variable [nodeId, (isvisited, distance, path)]
  var Result:Map[String, (Int, Int, List[String])] = Map()
  Result += ("A"-> (0,0,List()))
  Result += ("B"-> (0,10000,List()))
  Result += ("C"-> (0,10000,List()))
  Result += ("D"-> (0,10000,List()))
  Result += ("E"-> (0,10000,List()))
  Result += ("F"-> (0,10000,List()))


  val as = ActorSystem("Graph")
  // Creating the Same Graph nodes
  val NodeA = as.actorOf(Props[Node], "A")
  val NodeB = as.actorOf(Props[Node], "B")
  val NodeC = as.actorOf(Props[Node], "C")
  val NodeD = as.actorOf(Props[Node], "D")
  val NodeE = as.actorOf(Props[Node], "E")
  val NodeF = as.actorOf(Props[Node], "F")

  // Creating the Same edges
  var future = (NodeA ? DoubleLink(NodeB, 2 ))
  var r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeA --2-- NodeB")
  future = (NodeA ? DoubleLink(NodeC, 4))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeA --4-- NodeC")
  future = (NodeB ? DoubleLink(NodeC, 1))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeB --1-- NodeC")
  future = (NodeB ? DoubleLink(NodeD, 4))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeB --4-- NodeD")
  future = (NodeB ? DoubleLink(NodeE, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeB --2-- NodeE")
  future = (NodeC ? DoubleLink(NodeE, 3))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeC --3-- NodeE")
  future = (NodeD ? DoubleLink(NodeE, 3))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeD --3-- NodeE")
  future = (NodeD ? DoubleLink(NodeF, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeD --2-- NodeF")
  future = (NodeE ? DoubleLink(NodeF, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeE --2-- NodeF")
  
  //Dijkstra sequential
  var Current = "A"
  var queue : Map[String, Int] = Map()
  var counter = 0
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
          var path : List[String]= Result(Current)._3 ++ List(Current)
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

  
  //Dijkstra with Actors
  NodeA ! Start
  Thread.sleep(1000)
  NodeF ! Path
  NodeF ! Dist




  //assert
  future = (NodeA ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("A")._2)
  future = (NodeB ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("B")._2)
  future = (NodeC ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("C")._2)
  future = (NodeD ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("D")._2)
  future = (NodeE ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("E")._2)
  future = (NodeF ?  Dist)
  r = Await.result(future, 1 second)
  assert(r == Result("F")._2)
}
