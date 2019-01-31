package com.evolutiongaming.sample

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import com.evolutiongaming.sample.Account._
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.testkit.{ImplicitSender, TestKit}

class AccountSpec
  extends TestKit(ActorSystem("test"))
  with ImplicitSender
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private def createActor(id: UUID): ActorRef = {
    val actorRef = system.actorOf(Account.props(id))
    watch(actorRef)
  }

  private def terminate(actorRef: ActorRef): Unit = {
    actorRef ! PoisonPill
    expectTerminated(actorRef)
  }

  it should "support test scenario" in {
    val id = UUID.randomUUID()
    val actorRef = createActor(id)

    actorRef ! Subtract(2)
    expectMsg(Nack(s"Balance 0 is too low", Subtract(2)))

    actorRef ! Add(4)
    expectMsg(Ack(Add(4)))

    actorRef ! Subtract(5)
    expectMsg(Nack(s"Balance 4 is too low", Subtract(5)))

    actorRef ! Subtract(3)
    expectMsg(Ack(Subtract(3)))

    actorRef ! GetState
    expectMsg(State(1))

    terminate(actorRef)
  }

  it should "support recovery" in {
    val id = UUID.randomUUID()
    val initialInstance = createActor(id)

    val count = 15
    val amount = 4

    (1 to count) foreach { i =>
      initialInstance ! Add(amount)
      expectMsg(Ack(Add(amount)))

      initialInstance ! GetState
      expectMsg(State(amount * i))
    }

    terminate(initialInstance)

    // We allow the snapshot to get written here, if we don't then it will not yet be available for recovery
    // as it gets written asynchronously.
    // In general, Thread.sleep is bad and should be avoided, but in this case we use it to
    // illustrate that snapshots get written & used for recovery and doing this otherwise would
    // complicate the example.
    Thread.sleep(1000)

    val recovered = createActor(id)

    recovered ! GetState
    expectMsg(State(amount * count))

    terminate(recovered)
  }
}
