import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import slick.driver.H2Driver.api._
import slick.lifted.ProvenShape.proveShapeOf


object Example extends App {

  // Row representation:
  final case class Message(sender: String, content: String, id: Long = 0L)

  // Schema:
  final class MessageTable(tag: Tag) extends Table[Message](tag, "message") {
    def id      = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def sender  = column[String]("sender")
    def content = column[String]("content")
    def * = (sender, content, id) <> (Message.tupled, Message.unapply)
  }

  // Table:
  lazy val messages = TableQuery[MessageTable]

  // Database connection details:
  def db = Database.forConfig("chapter03")

  // Helper method for running a query in this example file
  def exec[T](program: DBIO[T]): T =
    Await.result(db.run(program), 500 milliseconds)

  def populate: DBIOAction[Option[Int], NoStream,Effect.All] =  for {
    //Create the table:
    _     <- messages.schema.create
    // Add some data:
    count <- messages ++= Seq(
      Message("Dave", "Hello, HAL. Do you read me, HAL?"),
      Message("HAL",  "Affirmative, Dave. I read you."),
      Message("Dave", "Open the pod bay doors, HAL."),
      Message("HAL",  "I'm sorry, Dave. I'm afraid I can't do that."))
  } yield count


  try {
    exec(populate)

    // Insert one, returning the ID:
    val id = exec((messages returning messages.map(_.id)) += Message("HAL", "I'm back"))

    println(s"The ID inserted was: $id")

    // Update HAL's name:
    val rows = exec(messages.filter(_.sender === "HAL").map(_.sender).update("HAL 9000"))

    // Update HAL's name and message:
    val query =
      messages.
        filter(_.id === 4L).
        map(message => (message.sender, message.content))

    val rowsAffected  = exec(query.update(("HAL 9000", "Sure, Dave. Come right in.")))

    messages.
      filter(_.id === 4L).
      map(message => (message.sender, message.content)).
      updateStatement
    
    exec(
      messages.
        filter(_.id === 4L).
        map(message => (message.sender, message.content)).
        update(("HAL 9000", "Sure, Dave. Come right in.")))    
    
    exec(messages returning messages.map(_.id) += Message("Dave", "Point taken." ))
    // Delete messages from HAL:
    // NB: will be zero rows affected because we've renamed HAL to HALL 9000
    exec(messages.filter(_.sender === "HAL").delete)

    def exclaim(msg: Message): Message = msg.copy(content = msg.content + "!")    
    
    for {
      msg <- exec(messages.result)
    } yield exec(messages.filter(_.id === msg.id).update(exclaim(msg)))
    
    def updateContent(id: Long) =
      messages.filter(_.id === id).map(_.content)

    def currentState() = {
         println("\nState of the database:")
         exec(messages.result.map(_.foreach(println)))
    }
    
    currentState
     
    try {
      exec {
        (
        updateContent(2L).update("Blue Mooon")                          andThen
        updateContent(3L).update("Please, anything but your singing ")  andThen
        messages.result.map(_.foreach { println })                      andThen
        DBIO.failed(new Exception("agggh my ears"))                     andThen
        updateContent(4L).update("That's incredibly hurtful")
        ).transactionally
      }
    
    } catch {
      case weKnow: Throwable => println("expected")
    }
        
    currentState
  } finally db.close

  //Exercises

  //Insert only once 
  def insertOnce(sender: String, text: String): Long = ???
  
  println(insertOnce("Dave","Have you changed the locks?") == insertOnce("Dave","Have you changed the locks?"))
  
  //Update Using a For Comprehension
  val rowsAffected = messages.
                     filter(_.sender === "HAL").
                     map(msg => (msg.sender)).
                     update("HAL 9000")  
  
  val rowsAffectedUsingForComprehension = ??? 
  
}