package com.svitovyda.watermarking

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.svitovyda.watermarking.WatermarkTaskActor._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class WatermarkTaskActorSpec extends TestKit(ActorSystem("MySpec"))
  with ImplicitSender
  with WordSpecLike
  with Eventually
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  private val testId = DocumentId.newId

  implicit val defaultPatience = PatienceConfig(timeout = Span(3, Seconds), interval = Span(500, Millis))

  "WatermarkTaskActor" must {

    "send Error and Failed status if empty" in {

      val actor = system.actorOf(WatermarkTaskActor.props)

      actor ! Request.StatusRequest(testId)
      expectMsg(Response.TaskResponse(testId, Status.Failed))

      actor ! Request.DocumentRequest(testId)
      expectMsgType[Response.Error]
    }

    "process correctly data" in {

      val actor = system.actorOf(WatermarkTaskActor.props)

      actor ! Request.WatermarkRequest(Content.Book, "The Dark Code", "Bruce Wayne", Some("Science"))
      val idBook: DocumentId = expectMsgPF() {
        case Response.TaskResponse(id, Status.Pending) => id
      }

      actor ! Request.WatermarkRequest(Content.Journal, "Journal of human flight routes", "Clark Kent")
      val idJournal: DocumentId = expectMsgPF() {
        case Response.TaskResponse(id, Status.Pending) => id
      }

      actor ! Request.StatusRequest(idBook)
      expectMsgPF() {
        case Response.TaskResponse(`idBook`, Status.InProgress) => true
      }

      actor ! Request.DocumentRequest(idBook)
      expectMsgType[Response.Error]

      actor ! Request.StatusRequest(idJournal)
      expectMsgPF() {
        case Response.TaskResponse(`idJournal`, Status.InProgress) => true
      }

      actor ! Request.StatusRequest(testId)
      expectMsg(Response.TaskResponse(testId, Status.Failed))

      eventually {
        actor ! Request.StatusRequest(idBook)
        expectMsgPF() {
          case Response.TaskResponse(`idBook`, Status.Ready) => true
        }
      }

      eventually {
        actor ! Request.StatusRequest(idJournal)
        expectMsgPF() {
          case Response.TaskResponse(`idJournal`, Status.Ready) => true
        }
      }

      actor ! Request.DocumentRequest(idBook)
      expectMsg(Response.Watermark(
        """{content:"book", title:"The Dark Code", author:"Bruce Wayne", topic:"Science"}"""
      ))

      actor ! Request.DocumentRequest(idJournal)
      expectMsg(Response.Watermark(
        """{content:"journal", title:"Journal of human flight routes", author:"Clark Kent"}"""
      ))

      actor ! Command.Failed(idJournal)
      actor ! Request.DocumentRequest(idJournal)
      expectMsgType[Response.Error]
    }
  }
}
