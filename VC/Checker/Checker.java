/**
 * Checker.java   
 * Mar 25 15:57:55 AEST 2020
 **/

package VC.Checker;

import VC.ASTs.*;
import VC.Scanner.SourcePosition;
import VC.ErrorReporter;
import VC.StdEnvironment;

public final class Checker implements Visitor {

	private String errMesg[] = {
		"*0: main function is missing",                            
		"*1: return type of main is not int",                    

		// defined occurrences of identifiers
		// for global, local and parameters
		"*2: identifier redeclared",                             
		"*3: identifier declared void",                         
		"*4: identifier declared void[]",                      

		// applied occurrences of identifiers
		"*5: identifier undeclared",                          

		// assignments
		"*6: incompatible type for =",                       
		"*7: invalid lvalue in assignment",                 

		 // types for expressions 
		"*8: incompatible type for return",                
		"*9: incompatible type for this binary operator", 
		"*10: incompatible type for this unary operator",

		 // scalars
		 "*11: attempt to use an array/function as a scalar", 

		 // arrays
		 "*12: attempt to use a scalar/function as an array",
		 "*13: wrong type for element in array initialiser",
		 "*14: invalid initialiser: array initialiser for scalar",   
		 "*15: invalid initialiser: scalar initialiser for array",  
		 "*16: excess elements in array initialiser",              
		 "*17: array subscript is not an integer",                
		 "*18: array size missing",                              

		 // functions
		 "*19: attempt to reference a scalar/array as a function",

		 // conditional expressions in if, for and while
		"*20: if conditional is not boolean",                    
		"*21: for conditional is not boolean",                  
		"*22: while conditional is not boolean",               

		// break and continue
		"*23: break must be in a while/for",                  
		"*24: continue must be in a while/for",              

		// parameters 
		"*25: too many actual parameters",                  
		"*26: too few actual parameters",                  
		"*27: wrong type for actual parameter",           

		// reserved for errors that I may have missed (J. Xue)
		"*28: misc 1",
		"*29: misc 2",

		// the following two checks are optional 
		"*30: statement(s) not reached",     
		"*31: missing return statement",    
	};


	private SymbolTable idTable;
	private static SourcePosition dummyPos = new SourcePosition();
	private ErrorReporter reporter;
	private Boolean function_return;

	// Checks whether the source program, represented by its AST, 
	// satisfies the language's scope rules and type rules.
	// Also decorates the AST as follows:
	//  (1) Each applied occurrence of an identifier is linked to
	//      the corresponding declaration of that identifier.
	//  (2) Each expression and variable is decorated by its type.

	public Checker (ErrorReporter reporter) {
		this.reporter = reporter;
		this.idTable = new SymbolTable ();
		establishStdEnvironment();
		function_return = false; 
	}

	public void check(AST ast) {
		ast.visit(this, null);
	}


	// auxiliary methods

	private void declareVariable(Ident ident, Decl decl) {
		IdEntry entry = idTable.retrieveOneLevel(ident.spelling);

		if (entry == null) {
			; // no problem
		} else
			reporter.reportError(errMesg[2] + ": %", ident.spelling, ident.position);
		idTable.insert(ident.spelling, decl);
	}

	private Expr i2f(Expr expr) {
		
		Operator sp = new Operator("i2f", expr.position);
		Expr afterConvert = new UnaryExpr(sp, expr, expr.position);
		afterConvert.parent = expr.parent;
		afterConvert.type = StdEnvironment.floatType;

		return afterConvert;
	}

	/**
	 * This func is used to check whether a statement is in For/While or not
	 * the purpose is to check the Break statement and Continue statement, these
	 * two statements must be in for or while
	 */
	private boolean inForAndWhile(AST ast) {

		boolean inside = false;
		while (ast != null) {
			if (ast instanceof ForStmt
				|| ast instanceof WhileStmt) {

				inside = true;
				break;
			} else {
				ast = ast.parent;
			}
		}

		return inside;
	}

	// =========================== Program ==========================

	@Override
	public Object visitProgram(Program ast, Object o) {

		/**
		 * Program is composed of declarations(global variable declarations and func)
		 * What we should do here is visiting it's child nodes which should be a list
		 * and check whether there is a main() and it's return value only can be int
		 */
		ast.FL.visit(this, null);
		Decl programAttr = idTable.retrieve("main");
		if (programAttr != null) {
			if (programAttr.isFuncDecl()) {
				if (!programAttr.T.isIntType()) {
					reporter.reportError(errMesg[1], "", ast.position);
				}
			} else {
				reporter.reportError(errMesg[0], "", ast.position);
			}
		} else {
			reporter.reportError(errMesg[0], "", ast.position);
		}
		return null;
	}

	// =========================== Declarations ========================

	/**
	 * For delarations, the first thing need to do is check if there is duplicate
	 * definitions for the declarations and then insert it to the symbol table
	 * 
	 * continue --> visit
	 */
	@Override
	public Object visitFuncDecl(FuncDecl ast, Object o) { 

		IdEntry dupEntry = idTable.retrieveOneLevel(ast.I.spelling);
		if (dupEntry != null) {
			reporter.reportError(errMesg[2]+": %", ast.I.spelling, ast.I.position);
		}
		/* if there is no duplicate entry at the same block --> insert */
		idTable.insert(ast.I.spelling, ast);
		// ast.PL.visit(this, ast);
		ast.S.visit(this, ast);
		/** check return value of func */
		if (!ast.T.isVoidType() && !ast.I.spelling.equals("main") && !function_return) {
			reporter.reportError(errMesg[31], "", ast.position);			
		}
		function_return = false;
		return null;
	}

	@Override
	public Object visitDeclList(DeclList ast, Object o) {
		ast.D.visit(this, null);
		ast.DL.visit(this, null);
		return null;
	}

	@Override
	public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
		return null;
	}

	@Override
	public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {

		/** firstly, check whether there is dupicate identifier */
		declareVariable(ast.I, ast);
		/** 
		 * Type
		 * int, float, boolean, int[], float[], boolean[], NOT void, void[]
		 */
		if (ast.T.isVoidType()) {
			reporter.reportError(errMesg[3]+": %", ast.I.spelling, ast.I.position);
		}
		if (ast.T.isArrayType()) {
			if (((ArrayType) ast.T).T.isVoidType()) {
				reporter.reportError(errMesg[4]+": %", ast.I.spelling, ast.I.position);
			}
			/** 
			 * array
			 * 1. explicitly point the length of array
			 * 2. or initialize it with expr
			 */
			if (((ArrayType) ast.T).E.isEmptyExpr() && !(ast.E instanceof InitExpr)) {
				reporter.reportError(errMesg[18]+": %", ast.I.spelling, ast.I.position);
			}

		}
		
		Object res = ast.E.visit(this, ast.T);
		if (!ast.T.isArrayType()) {
			if (!ast.T.assignable(ast.E.type)) {
				reporter.reportError(errMesg[6],"", ast.E.position);
			} else {
				if (!ast.T.equals(ast.E.type)) {// purpose of decorating the AST
					ast.E = i2f(ast.E);
				}
			}
		} else {
			if (ast.E instanceof InitExpr) {
				/** announced length must greater than the init length */
				Integer arraySize = (Integer) res;
				if (((ArrayType) ast.T).E.isEmptyExpr()) {
					((ArrayType) ast.T).E = 
						new IntExpr(new IntLiteral(arraySize.toString(), dummyPos), dummyPos);
				} else {
					Integer annonceLength =
						Integer.parseInt(((IntExpr)((ArrayType) ast.T).E).IL.spelling);
					/* the length we announced must greater than the init */
					if (arraySize > annonceLength) {
						reporter.reportError(errMesg[16]+": %", ast.I.spelling, ast.I.position);
					} 
				}
			} else if (!ast.E.isEmptyExpr()){
				reporter.reportError(errMesg[15]+": %", ast.I.spelling, ast.position);
			}
		}

		return null;
	}

	@Override
	public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {

		/** check the identifier already exists in symble table or not */
		declareVariable(ast.I, ast);
		
		if (ast.T.isVoidType()) {
			reporter.reportError(errMesg[3]+": %", ast.I.spelling, ast.position);
		} 

		if (ast.T.isArrayType()) {
			if (((ArrayType) ast.T).T.isVoidType()) {
				reporter.reportError(errMesg[4]+": %", ast.I.spelling, ast.position);
			}
			if (!(ast.E instanceof InitExpr) && ((ArrayType) ast.T).E.isEmptyExpr()) {
				reporter.reportError(errMesg[18]+": %", ast.I.spelling, ast.position);
			}
		}

		Object res = ast.E.visit(this, ast.T);
		if (ast.T.isArrayType()) {
			if (ast.E instanceof InitExpr) {
				Integer arraySize = (Integer) res;
				if (!((ArrayType) ast.T).E.isEmptyExpr()) {
					Integer announceLength = 
						Integer.parseInt(((IntExpr)((ArrayType) ast.T).E).IL.spelling);
					if (arraySize > announceLength) {
						reporter.reportError(errMesg[16]+": %", ast.I.spelling, ast.position);
					}
				} else {
					((ArrayType) ast.T).E = 
						new IntExpr(new IntLiteral(arraySize.toString(), dummyPos), dummyPos);
				}
			} else if (!ast.E.isEmptyExpr()) {
				reporter.reportError(errMesg[15]+": %", ast.I.spelling, ast.position);
			} 
		} else {
			if (!ast.T.assignable(ast.E.type)) {
				reporter.reportError(errMesg[6]+": %", "", ast.E.position);
			} else {
				if (!ast.T.equals(ast.E.type)) {
					ast.E = i2f(ast.E);
				} 
			}
		}

		return null;
	}

	// =========================== Statements ======================

	@Override
	public Object visitCompoundStmt(CompoundStmt ast, Object o) {

		idTable.openScope();
		/**
		 * Compound stmt:
		 * 1. come from funcs
		 * 2. not funcs
		 * 
		 * 3. important here: put the parameters of function into the Stmt
		 */
		if (o instanceof FuncDecl) {
			((FuncDecl) o).PL.visit(this, null);
		}
		ast.DL.visit(this, null);
		ast.SL.visit(this, o);
		idTable.closeScope();
		return null;
	}

	@Override
	public Object visitStmtList(StmtList ast, Object o) {

		ast.S.visit(this, o);
		/** need to check if there is error of "statement(s) not reached" */
		/** current statement is RETURN while the following statements exist */
		if (ast.S instanceof ReturnStmt && ast.SL instanceof StmtList) {
			reporter.reportError(errMesg[30], "", ast.SL.position);
		}
		ast.SL.visit(this, o);
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt ast, Object o) {
		
		/** According to the VC definition if expr only can be boolean expr */
		Type ifExprType = (Type) ast.E.visit(this, null);
		if (ifExprType == null) {
			reporter.reportError(errMesg[20]+" (found: null)", "", ast.E.position);
		} else {
			if (!ifExprType.isBooleanType()) {
				reporter.reportError(errMesg[20]+" (found: %)", ifExprType.toString(), ast.E.position);
			}
		}
		/** continue to visiting the left statements */
		ast.S1.visit(this, o);
		ast.S2.visit(this, o);
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt ast, Object o) {

		Type whileExprType = (Type) ast.E.visit(this, null);
		if (whileExprType == null) {
			reporter.reportError(errMesg[22]+" (found: null)", "", ast.E.position);
		} else {
			if (!whileExprType.isBooleanType()) {
				reporter.reportError(errMesg[22]+" (found: %)", whileExprType.toString(), ast.E.position);
			}
		}
		ast.S.visit(this, o);
		return null;
	}

	@Override
	public Object visitForStmt(ForStmt ast, Object o) {

		ast.E1.visit(this, null);
		Type forExprType = (Type) ast.E2.visit(this, null);
		if (forExprType == null) {
			reporter.reportError(errMesg[21]+" (found: null)", "", ast.E2.position);
		} else {
			if (!forExprType.isBooleanType()) {
				reporter.reportError(errMesg[21]+" (found: %)", forExprType.toString(), ast.E2.position);
			}
		}
		ast.E3.visit(this, null);
		ast.S.visit(this, o);
		return null;
	}

	@Override
	public Object visitBreakStmt(BreakStmt ast, Object o) {

		boolean inside = inForAndWhile(ast.parent);
		if (!inside) {
			reporter.reportError(errMesg[23], "break-statement", ast.position);
		}

		return null;
	}

	@Override
	public Object visitContinueStmt(ContinueStmt ast, Object o){

		boolean inside = inForAndWhile(ast.parent);
		if (!inside) {
			reporter.reportError(errMesg[24], "continue-statement", ast.position);
		}
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt ast, Object o) {

		/**
		 * handle return statement
		 * 
		 * handle return statement
		 * 
		 * handle return statement
		 */
		Type fType = ((FuncDecl) o).T;
		Type reType = (Type) ast.E.visit(this, null);
		function_return = true;
		if ((!fType.isVoidType() && ast.E.isEmptyExpr()) 
			||(fType.isVoidType() && !ast.E.isEmptyExpr())) {
			
			/** return not in function or function has no return */
			reporter.reportError(errMesg[8], "", ast.position);
		} else if (!fType.isVoidType() && !ast.E.isEmptyExpr()) {
			if (!fType.assignable(reType)) {
				reporter.reportError(errMesg[8], "", ast.position);
			} else {
				if (!fType.equals(reType)) {
					/**  coersion */
					ast.E = i2f(ast.E);
				}
			}
		}
		return null;
	}

	@Override
	public Object visitExprStmt(ExprStmt ast, Object o) {
		ast.E.visit(this, o);
		return null;
	}

	@Override
	public Object visitEmptyStmt(EmptyStmt ast, Object o) {
		return null;
	}

	@Override
	public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
		return null;
	}

	@Override
	public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
		return null;
	}

	// =========================== Expressions ======================

	// Returns the Type denoting the type of the expression. Does
	// not use the given object.

	@Override
	public Object visitEmptyExpr(EmptyExpr ast, Object o) {
		ast.type = StdEnvironment.errorType;
		return ast.type;
	}

	@Override
	public Object visitBooleanExpr(BooleanExpr ast, Object o) {
		ast.type = StdEnvironment.booleanType;
		return ast.type;
	}

	@Override
	public Object visitIntExpr(IntExpr ast, Object o) {
		ast.type = StdEnvironment.intType;
		return ast.type;
	}

	@Override
	public Object visitFloatExpr(FloatExpr ast, Object o) {
		ast.type = StdEnvironment.floatType;
		return ast.type;
	}

	@Override
	public Object visitStringExpr(StringExpr ast, Object o) {
		ast.type = StdEnvironment.stringType;
		return ast.type;
	}

	@Override
	public Object visitVarExpr(VarExpr ast, Object o) {
		ast.type = (Type) ast.V.visit(this, null);
		return ast.type;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr ast, Object o) {

		Type exprType = (Type) ast.E.visit(this, null);
		if (ast.O.spelling.equals("+") || ast.O.spelling.equals("-")) {
			if (exprType.isIntType() || exprType.isFloatType()) {
				ast.type = exprType;
			} else {
				reporter.reportError(errMesg[10]+": %", ast.O.spelling, ast.E.position);
				ast.type = StdEnvironment.errorType;
			}
		} else if (ast.O.spelling.equals("!")) {
			if (!exprType.isBooleanType()) {
				reporter.reportError(errMesg[10]+": %", ast.O.spelling, ast.position);
				ast.type = StdEnvironment.errorType;
			} else {
				ast.type = exprType;
			}
		}

		if (ast.type.isFloatType()) {
			ast.O.spelling = "f" + ast.O.spelling;
		} else if (ast.type.isBooleanType() || ast.type.isIntType()) {
			ast.O.spelling = "i" + ast.O.spelling;
		}

		return ast.type;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr ast, Object o) {

		Type ex1Type = (Type) ast.E1.visit(this, null);
		Type ex2Type = (Type) ast.E2.visit(this, null);
		String operator = ast.O.spelling;
		
		/**
		 * Type coercion: go only from int to float
		 * ex1:int, ex2:int --> not coercion
		 * ex1:float, ex2:float --> not coercion
		 * ex1:int, ex2:float or ex1:float, ex2:int --> coercion as float
		 * ex1:errorType, ex2:errorType --> not coercion
		 * ex1 or ex2 is errorType --> report error
		 */
		if (ex1Type.equals(ex2Type) && !ex1Type.isErrorType() && !ex2Type.isErrorType()) {
			ast.type = ex1Type;
		} else if (ex1Type.isIntType() && ex2Type.isFloatType()) {
			ast.E2 = i2f(ast.E2);
			ast.type = ex2Type;
		} else if (ex1Type.isFloatType() && ex2Type.isIntType()) {
			ast.E1 = i2f(ast.E1);
			ast.type = ex1Type;
		} else if (ex1Type.isErrorType() && ex2Type.isErrorType()) {
			ast.type = ex1Type;
		} else {
			reporter.reportError(errMesg[9]+": %", ast.O.spelling, ast.position);
			ast.type = StdEnvironment.errorType;
		}

		/**
		 * check the type according to the operator
		 * 1. &&,||,! can only be used on boolean values(Type)
		 * 2. + - * / --> overload
		 * 3. < <= > >= --> reset the ast.type as boolean
		 * 4. == and != --> pairs
		 */
		if (!ast.type.isErrorType()) {
			if (operator.equals("==") || operator.equals("!=")) {
				if (ast.type.isIntType() || ast.type.isBooleanType()) {
					ast.O.spelling = "i" + ast.O.spelling;
					ast.type = StdEnvironment.booleanType;
				} else if (ast.type.isFloatType()) {
					ast.O.spelling = "f" + ast.O.spelling;
					ast.type = StdEnvironment.booleanType;
				} else {
					reporter.reportError(errMesg[9]+": %", ast.O.spelling, ast.position);
					ast.type = StdEnvironment.errorType;
				}
			}

			if (operator.equals("<") || operator.equals("<=")
				|| operator.equals(">") || operator.equals(">=")) {
				
				if (ast.type.isIntType()) {
					ast.O.spelling = "i" + ast.O.spelling;
					ast.type = StdEnvironment.booleanType;
				} else if (ast.type.isFloatType()) {
					ast.O.spelling = "f" + ast.O.spelling;
					ast.type = StdEnvironment.booleanType;
				} else {
					reporter.reportError(errMesg[9]+": %", ast.O.spelling, ast.position);
					ast.type = StdEnvironment.errorType;
				}
			}

			if (operator.equals("+") || operator.equals("-") 
				|| operator.equals("*") || operator.equals("/")) {
				
				if (ast.type.isIntType()) {
					ast.O.spelling = "i" + ast.O.spelling;
					ast.type = StdEnvironment.intType;
				} else if (ast.type.isFloatType()) {
					ast.O.spelling = "f" + ast.O.spelling;
					ast.type = StdEnvironment.floatType;
				} else {
					reporter.reportError(errMesg[9]+": %", ast.O.spelling, ast.position);
					ast.type = StdEnvironment.errorType;
				}
			}

			if (operator.equals("&&") || operator.equals("||") || operator.equals("!")) {
				if (!ast.type.isBooleanType()) {
					reporter.reportError(errMesg[9]+": %", ast.O.spelling, ast.position);
					ast.type = StdEnvironment.errorType;
				} else {
					ast.O.spelling = "i" + ast.O.spelling;
				}
			}

		}
		return ast.type;
	}

	@Override
	public Object visitInitExpr(InitExpr ast, Object o) {

		/** When build the InitExpr object the only case is: init for array */
		/** there are a lot of exprs for array initializer and they are enclosed */
		/** in a IL(initializer list), we need to visit them recursively */
		/** example int a[] = {0, 1}, b[] = {3, 1}; */
		if (!((Type) o).isArrayType()) {
			reporter.reportError(errMesg[14], "", ast.position);
			ast.type = StdEnvironment.errorType;
			return ast.type;
		}
		ast.type = (Type) o;

		/** recursively visit */
		return ast.IL.visit(this, ((ArrayType) ast.type).T);
	}

	@Override
	public Object visitExprList(ExprList ast, Object o) {

		/** expr list for array initializer */
		/** int a[] = {0, 1}, b[] = {3, 1}; */
		Type defType = (Type) o;
		ast.E.visit(this, null);
		if (!defType.assignable(ast.E.type)) {
			reporter.reportError(errMesg[13]+": at position %", Integer.toString(ast.index), ast.E.position);
		} else {
			if (!defType.equals(ast.E.type)) {
				ast.E = i2f(ast.E);
			}
		}
	
		Integer numberElem = null;
		if (!ast.EL.isEmpty()) {
		   ((ExprList) ast.EL).index = ast.index + 1;
		   numberElem = (Integer) ast.EL.visit(this, o);
		} else {// reach the last one, return number back
			numberElem = ast.index + 1;
		}
		return numberElem;
	}

	/**
	 * visit array exprssion:
	 * arrID[int]
	 * arrID should be ArrayType
	 * the index should only be IntType
	 */
	@Override
	public Object visitArrayExpr(ArrayExpr ast, Object o) {

		Type arrIDType = (Type ) ast.V.visit(this, null);
		if (!arrIDType.isArrayType()) {
			reporter.reportError(errMesg[12], "", ast.position);
			ast.type = StdEnvironment.errorType;
		} else {
			ast.type = ((ArrayType) arrIDType).T;
		}
		Type exprType = (Type) ast.E.visit(this, null);
		if (!exprType.isIntType()) {
			reporter.reportError(errMesg[17], "", ast.position);
			ast.type = StdEnvironment.errorType;
		}
		
		return ast.type;
	}

	@Override
	public Object visitCallExpr(CallExpr ast, Object o) {

		/** retrive if the function is existing or not */
		Decl function = idTable.retrieve(ast.I.spelling);
		if (function == null) {
			reporter.reportError(errMesg[5]+": %", ast.I.spelling, ast.position);
			ast.type = StdEnvironment.errorType;
		} else if (function.isFuncDecl()) {
			/** here used for visit the argument expr PL is the formal parameter */
			ast.AL.visit(this, ((FuncDecl) function).PL);
			ast.type = function.T;
		} else {
			reporter.reportError(errMesg[19]+": %", ast.I.spelling, ast.I.position);
			ast.type = StdEnvironment.errorType;
		}

		return ast.type;
	}

	/** may have some problems here */
	/** may have some problems here */
	/** may have some problems here */
	/** may have some problems here */
	/** may have some problems here */
	@Override
	public Object visitAssignExpr(AssignExpr ast, Object o) {

		/** check left expr type and right expr type */
		Type e1Type = (Type) ast.E1.visit(this, o);
		Type e2Type = (Type) ast.E2.visit(this, o);

		if (!e1Type.assignable(e2Type)) {
			reporter.reportError(errMesg[6], "", ast.position);
			ast.type = StdEnvironment.errorType;
		} else {
			if (!e1Type.equals(e2Type)) {
				ast.E2 = i2f(ast.E2);
				ast.type = StdEnvironment.floatType;
			} else {
				ast.type = e1Type;
			}
		}

		if (ast.E1 instanceof VarExpr) {
			Decl var = idTable.retrieve(((SimpleVar)((VarExpr) ast.E1).V).I.spelling);
			if (var != null) {
				if (var.isFuncDecl()) {
					reporter.reportError(errMesg[7]+": %", ((SimpleVar)((VarExpr) ast.E1).V).I.spelling,
						 ast.position);
				}
			}
		} else if (!(ast.E1 instanceof ArrayExpr)){
			reporter.reportError(errMesg[7], "", ast.position);
			ast.type = StdEnvironment.errorType;
		}
		return ast.type;
	}

	@Override
	public Object visitEmptyExprList(EmptyExprList ast, Object o) {
		return null;
	}

	// Literals, Identifiers and Operators

	@Override
	public Object visitIdent(Ident I, Object o) {
		Decl binding = idTable.retrieve(I.spelling);
		if (binding != null)
			I.decl = binding;
		return binding;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral SL, Object o) {
		return StdEnvironment.booleanType;
	}

	@Override
	public Object visitIntLiteral(IntLiteral IL, Object o) {
		return StdEnvironment.intType;
	}

	@Override
	public Object visitFloatLiteral(FloatLiteral IL, Object o) {
		return StdEnvironment.floatType;
	}

	@Override
	public Object visitStringLiteral(StringLiteral IL, Object o) {
		return StdEnvironment.stringType;
	}

	@Override
	public Object visitOperator(Operator O, Object o) {
		return null;
	}

	// =========================== Parameters ======================

 	// Always returns null. Does not use the given object.

	@Override
	public Object visitParaList(ParaList ast, Object o) {
		ast.P.visit(this, null);
		ast.PL.visit(this, null);
		return null;
	}

	@Override
	public Object visitParaDecl(ParaDecl ast, Object o) {

		declareVariable(ast.I, ast);

		if (ast.T.isVoidType()) {
			reporter.reportError(errMesg[3] + ": %", ast.I.spelling, ast.I.position);
		} else if (ast.T.isArrayType()) {
		 if (((ArrayType) ast.T).T.isVoidType())
				reporter.reportError(errMesg[4] + ": %", ast.I.spelling, ast.I.position);
		}
		return null;
	}

	@Override
	public Object visitEmptyParaList(EmptyParaList ast, Object o) {
		return null;
	}

	// =========================== Arguments ======================
	@Override
	public Object visitArgList(ArgList ast, Object o) {

		/** the formal parameter list is passed by object o --> visitCallExpr */
		List formalParaList = (List) o;
		if (!formalParaList.isEmptyParaList()) {
			ast.A.visit(this, ((ParaList) formalParaList).P);
			ast.AL.visit(this, ((ParaList) formalParaList).PL);
		} else {
			/** this case: actual number of para > formal number of parameter */
			reporter.reportError(errMesg[25], "", ast.position);
		}

		return null;
	}

	@Override
	public Object visitEmptyArgList(EmptyArgList ast, Object o) {

		/** the formal parameter list is passed by object o --> visitCallExpr */
		List formalParaList = (List) o;
		if (!formalParaList.isEmptyParaList()) {
			/** this case: actual number of para < formal number of parameter */
			reporter.reportError(errMesg[26], "", ast.parent.position);
		}

		return null;
	}

	@Override
	public Object visitArg(Arg ast, Object o) {

		Type actulArg = (Type) ast.E.visit(this, null);
		Type formalArg = (Type) ((ParaDecl) o).T;
		if (!actulArg.isArrayType() && !formalArg.isArrayType()) {
			if (!formalArg.assignable(actulArg)) {
				reporter.reportError(errMesg[27]+": %", ((ParaDecl) o).I.spelling, ast.E.position);
			} else {
				if (!formalArg.equals(actulArg)) {
					ast.E = i2f(ast.E);
				}
			}
		} else if (actulArg.isArrayType() && formalArg.isArrayType()) {
			/** check if the element type is compatible */
			if (!((ArrayType) formalArg).T.assignable(((ArrayType) actulArg).T)) {
				reporter.reportError(errMesg[27]+": %", ((ParaDecl) o).I.spelling, ast.E.position);
			}
		} else {
			reporter.reportError(errMesg[27]+": %", ((ParaDecl) o).I.spelling, ast.E.position);
		}

		return null;
	}
	// Types 

	// Returns the type predefined in the standard environment. 

	@Override
	public Object visitErrorType(ErrorType ast, Object o) {
		return StdEnvironment.errorType;
	}

	@Override
	public Object visitBooleanType(BooleanType ast, Object o) {
		return StdEnvironment.booleanType;
	}

	@Override
	public Object visitIntType(IntType ast, Object o) {
		return StdEnvironment.intType;
	}

	@Override
	public Object visitFloatType(FloatType ast, Object o) {
		return StdEnvironment.floatType;
	}

	@Override
	public Object visitStringType(StringType ast, Object o) {
		return StdEnvironment.stringType;
	}

	@Override
	public Object visitVoidType(VoidType ast, Object o) {
		return StdEnvironment.voidType;
	}

	@Override
	public Object visitArrayType(ArrayType ast, Object o) {
		return ast;
	}

	// --------------------------- ******** -----------------------
	// =========================== SimpleVar ======================
	// --------------------------- ******** -----------------------

	@Override
	public Object visitSimpleVar(SimpleVar ast, Object o) {

		Decl var = idTable.retrieve(ast.I.spelling);
		if (var == null) {
			reporter.reportError(errMesg[5]+": %", ast.I.spelling, ast.I.position);
			ast.type = StdEnvironment.errorType;
		} else if (var.isFuncDecl()) {
			reporter.reportError(errMesg[11]+": %", ast.I.spelling, ast.I.position);
			ast.type = StdEnvironment.errorType;
		} else {
			ast.type = var.T;
		}

		if (ast.type.isArrayType() && (ast.parent instanceof VarExpr)
			&& !(ast.parent.parent instanceof Arg)) {
			
			reporter.reportError(errMesg[11]+": %", ast.I.spelling, ast.position);
			ast.type = StdEnvironment.errorType;
		}

		return ast.type;
	}


	// Creates a small AST to represent the "declaration" of each built-in
	// function, and enters it in the symbol table.

	private FuncDecl declareStdFunc (Type resultType, String id, List pl) {

		FuncDecl binding;

		binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl, 
					 new EmptyStmt(dummyPos), dummyPos);
		idTable.insert (id, binding);
		return binding;
	}

	// Creates small ASTs to represent "declarations" of all 
	// build-in functions.
	// Inserts these "declarations" into the symbol table.

	private final static Ident dummyI = new Ident("x", dummyPos);

	private void establishStdEnvironment () {

		// Define four primitive types
		// errorType is assigned to ill-typed expressions

		StdEnvironment.booleanType = new BooleanType(dummyPos);
		StdEnvironment.intType = new IntType(dummyPos);
		StdEnvironment.floatType = new FloatType(dummyPos);
		StdEnvironment.stringType = new StringType(dummyPos);
		StdEnvironment.voidType = new VoidType(dummyPos);
		StdEnvironment.errorType = new ErrorType(dummyPos);

		// enter into the declarations for built-in functions into the table

		StdEnvironment.getIntDecl = declareStdFunc( StdEnvironment.intType,
			"getInt", new EmptyParaList(dummyPos)); 
		StdEnvironment.putIntDecl = declareStdFunc( StdEnvironment.voidType,
			"putInt", new ParaList(
				new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
				new EmptyParaList(dummyPos), dummyPos)); 
		StdEnvironment.putIntLnDecl = declareStdFunc( StdEnvironment.voidType,
			"putIntLn", new ParaList(
				new ParaDecl(StdEnvironment.intType, dummyI, dummyPos),
				new EmptyParaList(dummyPos), dummyPos)); 
		StdEnvironment.getFloatDecl = declareStdFunc( StdEnvironment.floatType,
			"getFloat", new EmptyParaList(dummyPos)); 
		StdEnvironment.putFloatDecl = declareStdFunc( StdEnvironment.voidType,
			"putFloat", new ParaList(
				new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
				new EmptyParaList(dummyPos), dummyPos)); 
		StdEnvironment.putFloatLnDecl = declareStdFunc( StdEnvironment.voidType,
			"putFloatLn", new ParaList(
				new ParaDecl(StdEnvironment.floatType, dummyI, dummyPos),
				new EmptyParaList(dummyPos), dummyPos)); 
		StdEnvironment.putBoolDecl = declareStdFunc( StdEnvironment.voidType,
			"putBool", new ParaList(
			new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
			new EmptyParaList(dummyPos), dummyPos)); 
		StdEnvironment.putBoolLnDecl = declareStdFunc( StdEnvironment.voidType,
			"putBoolLn", new ParaList(
			new ParaDecl(StdEnvironment.booleanType, dummyI, dummyPos),
			new EmptyParaList(dummyPos), dummyPos)); 

		StdEnvironment.putStringLnDecl = declareStdFunc( StdEnvironment.voidType,
			"putStringLn", new ParaList(
			new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
			new EmptyParaList(dummyPos), dummyPos)); 

		StdEnvironment.putStringDecl = declareStdFunc( StdEnvironment.voidType,
			"putString", new ParaList(
			new ParaDecl(StdEnvironment.stringType, dummyI, dummyPos),
			new EmptyParaList(dummyPos), dummyPos)); 

		StdEnvironment.putLnDecl = declareStdFunc( StdEnvironment.voidType,
			"putLn", new EmptyParaList(dummyPos));
	}

}
