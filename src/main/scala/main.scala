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
      println("I visited node " + self)
      if (! q.isEmpty)
      {
        q.toSeq.sortBy(_._2).head._1 ! Visited(q - q.toSeq.sortBy(_._2).head._1)
      }
      else
      {
        println("Done")
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
  }
}

// for loop creat nodes
  // as an actor + sequential graph
// for loop creat edges
  // as an actor + sequential graph
//p = actor.solve
//r = sequential.solve
//assert(r == p)
object GraphSystem extends App {
  import Messages._
  implicit val to = Timeout(10 seconds)
  
  val as = ActorSystem("Graph")

  // Creating the Graph nodes
  val NodeA = as.actorOf(Props[Node], "A")
  val NodeB = as.actorOf(Props[Node], "B")
  val NodeC = as.actorOf(Props[Node], "C")
  val NodeD = as.actorOf(Props[Node], "D")
  val NodeE = as.actorOf(Props[Node], "E")
  val NodeF = as.actorOf(Props[Node], "F")

  // Creating adding the connections
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
    println("Node --2-- NodeF")
  future = (NodeE ? DoubleLink(NodeF, 2))
  r = Await.result(future, 1 second)
  if (r == Success)
    println("NodeE --2-- NodeF")
  
  // Visiting
  NodeA ! Start
  Thread.sleep(1000)
  NodeF ! Path
}
