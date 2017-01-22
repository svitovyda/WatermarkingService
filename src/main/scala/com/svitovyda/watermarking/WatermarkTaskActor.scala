package com.svitovyda.watermarking

import java.util.UUID

import akka.actor.{Actor, Props}
import com.svitovyda.watermarking.WatermarkTaskActor._

import scala.concurrent.Future

class WatermarkTaskActor extends Actor {

  private implicit val dispatcher = context.system.dispatcher

  var tasks: Map[DocumentId, Status] = Map()
  var documents: Map[DocumentId, Response.Watermark] = Map()

  override def receive: Receive = {
    case request: Request.WatermarkRequest =>
      val documentId = DocumentId.newId
      tasks += documentId -> Status.Pending
      sender() ! Response.TaskResponse(documentId)
      Future {
        self ! Command.Started(documentId)
        Thread.sleep(1000L + (math.random * 1500).toLong) // let's emulate very heavy process
        self ! Command.Ready(documentId, Response.Watermark(request)) // or it could be obtained from other system
      }.recover { case e: Throwable =>
        self ! Command.Failed(documentId)
      }

    case Request.StatusRequest(documentId) =>
      sender() ! Response.TaskResponse(documentId, tasks.getOrElse(documentId, Status.Failed))

    case Request.DocumentRequest(documentId) =>
      val result = for {
        status <- tasks.get(documentId)
        if status == Status.Ready
        watermark <- documents.get(documentId)
      } yield watermark

      result match {
        case Some(watermark) => sender() ! watermark
        case None            => sender() ! Response.Error("Document ")
      }

    case Command.Started(documentId) =>
      tasks = tasks.updated(documentId, Status.InProgress)

    case Command.Ready(documentId, watermark) =>
      documents += documentId -> watermark
      tasks = tasks.updated(documentId, Status.Ready)

    case Command.Failed(documentId) =>
      tasks = tasks.updated(documentId, Status.Failed)
  }

}

object WatermarkTaskActor {
  def props = Props(classOf[WatermarkTaskActor])

  sealed trait Content {
    def name = toString.toLowerCase
  }
  object Content {
    val Values: Set[Content] = Set(Book, Journal)

    def apply(code: String): Content = Values.find {
      _.toString.toLowerCase == code.toLowerCase
    }.getOrElse(sys.error(s"Cannot find the content type named: $code"))

    case object Book extends Content
    case object Journal extends Content
  }

  case class DocumentId(value: String) extends AnyVal
  object DocumentId {
    def newId: DocumentId = DocumentId(UUID.randomUUID.toString)
  }

  sealed trait Request
  object Request {
    case class WatermarkRequest(
      content: Content,
      title: String,
      author: String, // could be a List[String]
      topic: Option[String] = None // could be Option[Topic], Topic - another sealed trait,
                                   // if list of all topics is known
    ) extends Request with Command

    case class StatusRequest(documentId: DocumentId)
    case class DocumentRequest(documentId: DocumentId)
  }

  sealed trait Status
  object Status {
    val Values: Set[Status] = Set()

    case object Pending extends Status
    case object InProgress extends Status
    case object Ready extends Status
    case object Failed extends Status
  }

  sealed trait Response
  object Response {
    case class TaskResponse(documentId: DocumentId, status: Status = Status.Pending)

    case class Watermark(watermark: String)
    object Watermark {
      def apply(request: Request.WatermarkRequest): Watermark = {
        val content = List(
          s"""content:"${request.content.name}"""",
          s"""title:"${request.title}"""",
          s"""author:"${request.author}""""
        )
        val topic = request.topic.map(t => List(s"""topic:"$t"""")).getOrElse(Nil)
        new Watermark(
          s"{${(content ++ topic).mkString(", ")}}"
        )
      }
    }

    case class Error(message: String)
  }

  sealed trait Command
  object Command {
    case class Ready(documentId: DocumentId, watermark: Response.Watermark)
    case class Started(documentId: DocumentId)
    case class Failed(documentId: DocumentId)
  }

}
