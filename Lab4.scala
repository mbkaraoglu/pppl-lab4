package jsy.student

import jsy.lab4.Lab4Like

object Lab4 extends jsy.util.JsyApplication with Lab4Like {
  import jsy.lab4.ast._
  import jsy.lab4.Parser
  
  /*
   * CSCI 3155: Lab 4
   * <Your Name>
   * 
   * Partner: <Your Partner's Name>
   * Collaborators: <Any Collaborators>
   */

  /*
   * Fill in the appropriate portions above by replacing things delimited
   * by '<'... '>'.
   * 
   * Replace the '???' expression with your code in each function.
   *
   * Do not make other modifications to this template, such as
   * - adding "extends App" or "extends Application" to your Lab object,
   * - adding a "main" method, and
   * - leaving any failing asserts.
   *
   * Your lab will not be graded if it does not compile.
   *
   * This template compiles without error. Before you submit comment out any
   * code that does not compile or causes a failing assert. Simply put in a
   * '???' as needed to get something that compiles without error. The '???'
   * is a Scala expression that throws the exception scala.NotImplementedError.
   */
  
  /* Collections and Higher-Order Functions */
  
  /* Lists */
  
  def compressRec[A](l: List[A]): List[A] =  l match {
    case Nil | _ :: Nil => l
    case h1 :: (t1 @ (h2 :: _)) => if (h1 == h2) compressRec(t1) else h1::compressRec(t1)
  }
  
  def compressFold[A](l: List[A]): List[A] = l.foldRight(Nil: List[A]){
    (h, acc) =>  acc match {
      case Nil => if (h!= Nil) h::acc else acc
      case h1 :: _ => if (h1 != h) h::acc else acc
    }
  }
  
  def mapFirst[A](l: List[A])(f: A => Option[A]): List[A] = l match {
    case Nil => l
    case h :: t => f(h) match {
      case None => h :: mapFirst(t)(f)
      case Some(h1) => h1 :: t
    }
  }
  
  /* Trees */

  def foldLeft[A](t: Tree)(z: A)(f: (A, Int) => A): A = {
    def loop(acc: A, t: Tree): A = t match {
      case Empty => acc
      case Node(l, d, r) => loop(f(loop(acc,l),d),r)
    }
    loop(z, t)
  }

  // An example use of foldLeft
  def sum(t: Tree): Int = foldLeft(t)(0){ (acc, d) => acc + d }

  // Create a tree from a list. An example use of the
  // List.foldLeft method.
  def treeFromList(l: List[Int]): Tree =
    l.foldLeft(Empty: Tree){ (acc, i) => acc insert i }

  def strictlyOrdered(t: Tree): Boolean = {
    val (b, _) = foldLeft(t)((true, None: Option[Int])){
      (acc,y) => acc match {
        case (b1,None) => (b1,Some(y))
        case (b1,Some(x)) => if (x < y) (b1, Some(x)) else (false,Some(x))
      }
    }
    b
  }

  /* Type Inference */

  // While this helper function is completely given, this function is
  // worth studying to see how library methods are used.
  def hasFunctionTyp(t: Typ): Boolean = t match {
    case TFunction(_, _) => true
    case TObj(fields) if (fields exists { case (_, t) => hasFunctionTyp(t) }) => true
    case _ => false
  }
  
  def typeof(env: TEnv, e: Expr): Typ = {
    def err[T](tgot: Typ, e1: Expr): T = throw StaticTypeError(tgot, e1, e)

    e match {
      case Print(e1) => typeof(env, e1); TUndefined
      case N(_) => TNumber
      case B(_) => TBool
      case Undefined => TUndefined
      case S(_) => TString
      case Var(x) => env(x)
      case Decl(mode, x, e1, e2) => typeof(env + (x -> typeof(env,e1)), e2)
        //TypeNeg
      case Unary(Neg, e1) => typeof(env, e1) match {
        case TNumber => TNumber
        case tgot => err(tgot, e1)
      }
        //TypeNot
      case Unary(Not, e1) => if (typeof(env,e1) == TBool) TBool else err(typeof(env,e1),e1)
        //TypeArith for plus, TypePlusString
      case Binary(Plus, e1, e2) => typeof(env,e1) match {
        case  (TNumber) => if (typeof(env,e2) == TNumber) TNumber else err(typeof(env,e2),e2)
        case  (TString) => if (typeof(env,e2) == TString) TString else err(typeof(env,e2),e2)
        case _ => err(typeof(env,e1),e1)
      }
        //TypeArith
      case Binary(Minus|Times|Div, e1, e2) => typeof(env,e1) match {
        case  (TNumber) => if (typeof(env,e2) == TNumber) TNumber else err(typeof(env,e2),e2)
        case _ => err(typeof(env,e1),e1)
    }
        //TypeEquality
      case Binary(Eq|Ne, e1, e2) => {
        if (typeof(env,e1)==typeof(env,e2) && !hasFunctionTyp(typeof(env,e1))) TBool else err(typeof(env,e1),e1)
      }


        //TypeInequalityString,TypeInequalityNUmber
      case Binary(Lt|Le|Gt|Ge, e1, e2) => typeof(env,e1) match {
        case (TNumber) => if (typeof(env,e2) == TNumber) TBool else err(typeof(env,e2),e2)
        case TString => if (typeof(env,e2) == TString) TBool else err(typeof(env,e2),e2)
        case _ => err(typeof(env,e1),e1)
      }
        //TypeAndOr
      case Binary(And|Or, e1, e2) => typeof(env,e1) match {
        case TBool => if(typeof(env,e2) == TBool) TBool else err(typeof(env,e2),e2)
        case _ => err(typeof(env,e1),e1)
      }
        //TypeSeq - does e1 need to be type checked like the notes say?
      case Binary(Seq, e1, e2) => typeof(env,e1); typeof(env,e2)
        //TypeIf
      case If(e1, e2, e3) => typeof(env,e1) match {
        case TBool => if (typeof(env,e2) == typeof(env,e3)) typeof(env,e2) else err(typeof(env,e3),e3)
        case _ => err(typeof(env,e1),e1)
      }

      case Function(p, params, tann, e1) => {
        // Bind to env1 an environment that extends env with an appropriate binding if
        // the function is potentially recursive.
        val env1 = (p, tann) match {
          /***** Add cases here *****/
          case (Some(f),Some(tret)) => env + (f-> TFunction(params,tret))//extend(env,f,TFunction(params,tret))
          case (None,_) => env
          case _ => err(TUndefined, e1)
        }
        // Bind to env2 an environment that extends env1 with bindings for params.
        val env2 = params.foldLeft(env1){(acc,p1) => acc + (p1._1 -> p1._2.t)}
        val t1 = typeof(env2,e1)
        // Check with the possibly annotated return type
        tann match {
          case None => TFunction(params,typeof(env2,e1))
          case Some(tret) => if (t1 == tret) TFunction(params,tret) else err(tret,e)
        }

      }
      case Call(e1, args) => typeof(env, e1) match {
        case TFunction(params, tret) if (params.length == args.length) =>
          (params zip args).foreach {
            case ((x,t),ex) => if (t.t!= typeof(env,ex)) err(t.t,ex)
          };
          tret
        case tgot => err(tgot, e1)
      }
      case Obj(fields) => TObj(fields.mapValues(e1 => typeof(env,e1)))
      case GetField(e1, f) => typeof(env,e1) match {
        case TObj(x) => x.get(f) match {
          case Some(y) => y
          case None => err(typeof(env,e1),e1)
        }
        case _ =>  err(typeof(env,e1),e1)
      }
    }
  }
  
  
  /* Small-Step Interpreter */

  /*
   * Helper function that implements the semantics of inequality
   * operators Lt, Le, Gt, and Ge on values.
   *
   * We suggest a refactoring of code from Lab 2 to be able to
   * use this helper function in eval and step.
   *
   * This should the same code as from Lab 3.
   */
  def inequalityVal(bop: Bop, v1: Expr, v2: Expr): Boolean = {
    require(isValue(v1), s"inequalityVal: v1 ${v1} is not a value")
    require(isValue(v2), s"inequalityVal: v2 ${v2} is not a value")
    require(bop == Lt || bop == Le || bop == Gt || bop == Ge)
    (v1, v2) match {
      case (S(e1),S(e2)) => bop match {
        case Gt => e1 > e2
        case Ge => e1 >= e2
        case Lt => e1 < e2
        case Le => e1 <= e2
        case _ => ???
      }
      case (N(e1),N(e2)) => bop match {
        case Gt => e1 > e2
        case Ge => e1 >= e2
        case Lt => e1 < e2
        case Le => e1 <= e2
        case _ => ???
        }
      case _ => ???
      }


  }

  /* This should be the same code as from Lab 3 */
  def iterate(e0: Expr)(next: (Expr, Int) => Option[Expr]): Expr = {
    def loop(e: Expr, n: Int): Expr = ???
    loop(e0, 0)
  }

  /* Capture-avoiding substitution in e replacing variables x with esub. */
  def substitute(e: Expr, esub: Expr, x: String): Expr = {
    def subst(e: Expr): Expr = e match {
      case N(_) | B(_) | Undefined | S(_) => e
      case Print(e1) => Print(substitute(e1, esub, x))
        /***** Cases from Lab 3 */
      case Unary(uop, e1) => Unary(uop,subst(e1))
      case Binary(bop, e1, e2) => Binary(bop,subst(e1),subst(e2))
      case If(e1, e2, e3) => If(subst(e1),subst(e2),subst(e3))
      case Var(y) => if (x==y) esub else e
      case Decl(mode, y, e1, e2) => Decl(mode,y,subst(e1),if (x==y) e2 else subst(e2))
        /***** Cases needing adapting from Lab 3 */
      case Function(p, params, tann, e1) =>
        ???
      case Call(e1, args) => Call(subst(e1),args map subst)
        /***** New cases for Lab 4 */
      case Obj(fields) => Obj(fields.mapValues(esub => subst(esub)))
      case GetField(e1, f) => if (x!=f) GetField(subst(e1),f) else e
    }

    val fvs = freeVars(???)
    def fresh(x: String): String = if (???) fresh(x + "$") else x
    subst(e)
  }

  /* Rename bound variables in e */
  def rename(e: Expr)(fresh: String => String): Expr = {
    def ren(env: Map[String,String], e: Expr): Expr = {
      e match {
        case N(_) | B(_) | Undefined | S(_) => e
        case Print(e1) => Print(ren(env, e1))

        case Unary(uop, e1) => ???
        case Binary(bop, e1, e2) => ???
        case If(e1, e2, e3) => ???

        case Var(y) =>
          ???
        case Decl(mode, y, e1, e2) =>
          val yp = fresh(y)
          ???

        case Function(p, params, retty, e1) => {
          val (pp, envp): (Option[String], Map[String,String]) = p match {
            case None => ???
            case Some(x) => ???
          }
          val (paramsp, envpp) = params.foldRight( (Nil: List[(String,MTyp)], envp) ) {
            ???
          }
          ???
        }

        case Call(e1, args) => ???

        case Obj(fields) => ???
        case GetField(e1, f) => ???
      }
    }
    ren(empty, e)
  }

  /* Check whether or not an expression is reduced enough to be applied given a mode. */
  def isRedex(mode: Mode, e: Expr): Boolean = mode match {
    case MConst => ???
    case MName => ???
  }

  def step(e: Expr): Expr = {
    require(!isValue(e), s"step: e ${e} to step is a value")
    e match {
      /* Base Cases: Do Rules */
      case Print(v1) if isValue(v1) => println(pretty(v1)); Undefined
        /***** Cases needing adapting from Lab 3. */
      case Unary(Neg, N(v1)) => N(-v1)
      case Unary(Not, B(v1)) => B(!v1)
      case Binary(Seq,v1,e2) if isValue(v1) => e2
      case Binary(Plus,S(v1),S(v2)) => S(v1 + v2)
      case Binary(Plus,N(v1),N(v2)) => N(v1 + v2)
      case Binary(bop @ (Le|Ge|Gt|Lt), v1,v2) if isValue(v1) && isValue(v2)=> B(inequalityVal(bop,v1,v2))

        /***** More cases here */
      case Call(v1, args) if isValue(v1) =>
        v1 match {
          case Function(p, params, _, e1) => {
            val pazip = params zip args
            if (???) {
              val e1p = pazip.foldRight(e1) {
                ???
              }
              p match {
                case None => ???
                case Some(x1) => ???
              }
            }
            else {
              val pazipp = mapFirst(pazip) {
                ???
              }
              ???
            }
          }
          case _ => throw StuckError(e)
        }
        /***** New cases for Lab 4. */

      /* Inductive Cases: Search Rules */
      case Print(e1) => Print(step(e1))
        /***** Cases from Lab 3. */
      case Unary(uop, e1) => ???
        /***** More cases here */
        /***** Cases needing adapting from Lab 3 */
      case Call(v1 @ Function(_, _, _, _), args) => ???
      case Call(e1, args) => ???
        /***** New cases for Lab 4. */

      /* Everything else is a stuck error. Should not happen if e is well-typed.
       *
       * Tip: you might want to first develop by comment out the following line to see which
       * cases you have missing. You then uncomment this line when you are sure all the cases
       * that you have left the ones that should be stuck.
       */
      case _ => throw StuckError(e)
    }
  }
  
  
  /* External Interfaces */
  
  //this.debug = true // uncomment this if you want to print debugging information
  this.keepGoing = true // comment this out if you want to stop at first exception when processing a file
}

