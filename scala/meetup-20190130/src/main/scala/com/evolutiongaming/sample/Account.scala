package com.evolutiongaming.sample

import java.util.UUID

import akka.actor.{ActorLogging, Props}
import akka.persistence._
import com.evolutiongaming.sample.Account._

object Account {
  sealed trait Command
  case class Add(amount: BigDecimal) extends Command
  case class Subtract(amount: BigDecimal) extends Command

  sealed trait Event
  case class Added(amount: BigDecimal) extends Event
  case class Subtracted(amount: BigDecimal) extends Event

  type Error = String

  case object GetState
  case class State(balance: BigDecimal) {
    def withEvent(event: Event): State = event match {
      case Added(amount: BigDecimal)      => copy(balance = balance + amount)
      case Subtracted(amount: BigDecimal) => copy(balance = balance - amount)
    }
  }

  def validateCommand(state: State, cmd: Command): Either[Error, Event] = {
    def isPositive(x: BigDecimal)= Either.cond(x > 0, x, s"Amount is negative: $x")

    cmd match {
        case Add(x: BigDecimal)     =>
        for {
          x <- isPositive(x)
        } yield Added(x)

      case Subtract(x: BigDecimal)  =>
        def isSufficient(x: BigDecimal)= Either.cond(x <= state.balance, x, s"Balance ${state.balance} is too low")

        for {
          y <- isPositive(x)
          z <- isSufficient(y)
        } yield Subtracted(z)
    }
  }

  sealed trait Response
  case class Ack(command: Command) extends Response
  case class Nack(reason: Error, command: Command) extends Response

  def props(id: UUID): Props = Props(new Account(id))
}

class Account(id: UUID) extends PersistentActor with ActorLogging {
  private var state: State = State(0)

  override def persistenceId: String = s"account-$id"

  private def updateState(evt: Event): Unit = {
    val newState = state.withEvent(evt)
    log.info(s"{}: State will change from {} to {} due to {} (recoveryRunning = $recoveryRunning)", persistenceId, state, newState, evt)
    state = newState
  }

  override def receiveRecover: Receive = {
    case evt: Event                         =>
      updateState(evt)

    case RecoveryCompleted                  =>
      log.info("{}: Recovery completed", persistenceId)

    case SnapshotOffer(_, snapshot: State)  =>
      log.info("{}: Restoring state from snapshot {}", persistenceId, snapshot)
      state = snapshot
  }

  override def receiveCommand: Receive = {
    case GetState       =>
      sender() ! state

    case SaveSnapshotSuccess(metadata)         =>
      log.info("{}: Saved snapshot {}", persistenceId, metadata)

    case SaveSnapshotFailure(metadata, reason) =>
      log.error("{}: Failed to save snapshot {}: {}", persistenceId, metadata, reason)

    case cmd: Command   =>
      val snd = sender()

      validateCommand(state, cmd) match {
        case Left(error)      =>
          snd ! Nack(error, cmd)

        case Right(event)     =>
          persist(event) { event =>
            updateState(event)

            val SnapshotInterval = 10
            if (lastSequenceNr % SnapshotInterval == 0 && lastSequenceNr != 0) {
              log.info("{}: Saving snapshot {}", persistenceId, state)
              saveSnapshot(state)
            }

            snd ! Ack(cmd)
          }
      }

    case x =>
      log.error("{}: Unknown command {}", persistenceId, x)
  }
}
