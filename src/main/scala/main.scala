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
  case object Success
}

class Node extends Actor {
  import Messages._
  implicit val to = Timeout(10 seconds)

  // Map[ActorRef, Distance]
  var edge:Map[ActorRef,Int] = Map()

  override def receive : Receive = {
    case DoubleLink(nodeRef: ActorRef, distance : Int) =>
      edge = edge + (nodeRef -> distance)
      var future = (nodeRef ? SingleLink(self, distance))
      var r = Await.result(future, 1 second)
      sender ! r
    case SingleLink(nodeRef: ActorRef, distance : Int) =>
      edge = edge + (nodeRef -> distance)
      sender ! Success
  }
}

object GraphSystem extends App {
  import Messages._
  implicit val to = Timeout(10 seconds)
  
  val as = ActorSystem("Graph")

  // Creating the Graph nodes
  val Node0 = as.actorOf(Props[Node], "0")
  val Node1 = as.actorOf(Props[Node], "1")

  // Creating adding the connections
  var future = (Node1 ? DoubleLink(Node0, 6))
  var r = Await.result(future, 1 second)
  if (r == Success)
    println("Node0 --6-- Node1")
}
