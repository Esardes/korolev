/*
 * Copyright 2017-2020 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.cats

import _root_.cats.Traverse
import _root_.cats.effect._
import _root_.cats.effect.unsafe.IORuntime
import _root_.cats.instances.list._
import korolev.effect.{Effect => KEffect}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class IO3Effect(runtime: IORuntime) extends KEffect[IO] {

  def pure[A](value: A): IO[A] =
    IO.pure(value)

  def delay[A](value: => A): IO[A] =
    IO.delay(value)

  def fail[A](e: Throwable): IO[A] = IO.raiseError(e)

  def fork[A](m: => IO[A])(implicit ec: ExecutionContext): IO[A] =
    m.evalOn(ec)

  def unit: IO[Unit] =
    IO.unit

  def never[T]: IO[T] =
    IO.never

  def fromTry[A](value: => Try[A]): IO[A] =
    IO.fromTry(value)

  def start[A](m: => IO[A])(implicit ec: ExecutionContext): IO[KEffect.Fiber[IO, A]] = m
    .startOn(ec)
    .map { fiber =>
      new KEffect.Fiber[IO, A] {
        def join(): IO[A] = fiber.joinWithNever
      }
    }

  def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): IO[A] =
    IO.async_(cb)

  def promiseF[A](cb: (Either[Throwable, A] => Unit) => IO[Unit]): IO[A] =
    Async[IO].async(cb(_).as(None))

  def flatMap[A, B](m: IO[A])(f: A => IO[B]): IO[B] =
    m.flatMap(f)

  def map[A, B](m: IO[A])(f: A => B): IO[B] =
    m.map(f)

  def recover[A, AA >: A](m: IO[A])(f: PartialFunction[Throwable, AA]): IO[AA] =
    m.handleErrorWith(e => f.andThen(IO.pure[AA] _).applyOrElse(e, IO.raiseError[AA] _))

  def recoverF[A, AA >: A](m: IO[A])(f: PartialFunction[Throwable, IO[AA]]): IO[AA] =
    m.handleErrorWith(e => f.applyOrElse(e, IO.raiseError[AA] _))

  def sequence[A](in: List[IO[A]]): IO[List[A]] =
    Traverse[List].sequence(in)

  def runAsync[A](m: IO[A])(callback: Either[Throwable, A] => Unit): Unit =
    m.unsafeRunAsync(callback)(runtime)

  def run[A](m: IO[A]): Either[Throwable, A] =
    Try(m.unsafeRunSync()(runtime)).toEither

  def toFuture[A](m: IO[A]): Future[A] =
    m.unsafeToFuture()(runtime)
}
