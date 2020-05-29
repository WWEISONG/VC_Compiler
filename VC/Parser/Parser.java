/*
 * Parser.java            
 *
 * This parser for a subset of the VC language is intended to 
 *  demonstrate how to create the AST nodes, including (among others): 
 *  [1] a list (of statements)
 *  [2] a function
 *  [3] a statement (which is an expression statement), 
 *  [4] a unary expression
 *  [5] a binary expression
 *  [6] terminals (identifiers, integer literals and operators)
 *
 * In addition, it also demonstrates how to use the two methods start 
 * and finish to determine the position information for the start and 
 * end of a construct (known as a phrase) corresponding an AST node.
 *
 * NOTE THAT THE POSITION INFORMATION WILL NOT BE MARKED. HOWEVER, IT CAN BE
 * USEFUL TO DEBUG YOUR IMPLEMENTATION.
 *


program       -> func-decl
func-decl     -> type identifier "(" ")" compound-stmt
type          -> void
identifier    -> ID
// statements
compound-stmt -> "{" stmt* "}" 
stmt          -> expr-stmt
expr-stmt     -> expr? ";"
// expressions 
expr                -> additive-expr
additive-expr       -> multiplicative-expr
										|  additive-expr "+" multiplicative-expr
										|  additive-expr "-" multiplicative-expr
multiplicative-expr -> unary-expr
							|  multiplicative-expr "*" unary-expr
							|  multiplicative-expr "/" unary-expr
unary-expr          -> "-" unary-expr
				|  primary-expr

primary-expr        -> identifier
 				|  INTLITERAL
				| "(" expr ")"
 */

package VC.Parser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;
import VC.ASTs.*;

public class Parser {

	private Scanner scanner;
	private ErrorReporter errorReporter;
	private Token currentToken;
	private SourcePosition previousTokenPosition;
	private SourcePosition dummyPos = new SourcePosition();

	public Parser (Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		previousTokenPosition = new SourcePosition();

		currentToken = scanner.getToken();
	}

// match checks to see if the current token matches tokenExpected.
// If so, fetches the next token.
// If not, reports a syntactic error.

	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			previousTokenPosition = currentToken.position;
			currentToken = scanner.getToken();
		} else {
			syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
	}

	void accept() {
		previousTokenPosition = currentToken.position;
		currentToken = scanner.getToken();
	}

	void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
		SourcePosition pos = currentToken.position;
		errorReporter.reportError(messageTemplate, tokenQuoted, pos);
		throw(new SyntaxError());
	}

// start records the position of the start of a phrase.
// This is defined to be the position of the first
// character of the first token of the phrase.

	void start(SourcePosition position) {
		position.lineStart = currentToken.position.lineStart;
		position.charStart = currentToken.position.charStart;
	}

// finish records the position of the end of a phrase.
// This is defined to be the position of the last
// character of the last token of the phrase.

	void finish(SourcePosition position) {
		position.lineFinish = previousTokenPosition.lineFinish;
		position.charFinish = previousTokenPosition.charFinish;
	}

	void copyStart(SourcePosition from, SourcePosition to) {
		to.lineStart = from.lineStart;
		to.charStart = from.charStart;
	}

// ========================== PROGRAMS ========================

	public Program parseProgram() {

		Program programAST = null;
		
		SourcePosition programPos = new SourcePosition();
		start(programPos);

		try {
			List dlAST = parseCompoundDeclList();
			finish(programPos);
			programAST = new Program(dlAST, programPos); 
			if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" unknown type", currentToken.spelling);
			}
		}
		catch (SyntaxError s) { return null; }
		return programAST;
	}

// ========================== DECLARATIONS ========================

	List parseCompoundDeclList() throws SyntaxError {

		List dlAST = null;
		Decl deAST = null;
		SourcePosition dlPos = new SourcePosition();
		start(dlPos);

		if (currentToken.kind != Token.EOF) {
			Type typeAST = parseType();
			Ident idAST = parseIdent();
			if (currentToken.kind == Token.LPAREN) {
				deAST = parseFuncDecl(typeAST, idAST);
				if (currentToken.kind != Token.EOF) {
					dlAST = parseCompoundDeclList();
					finish(dlPos);
					dlAST = new DeclList(deAST, dlAST, dlPos);
				} else if (deAST != null) {
					finish(dlPos);
					dlAST = 
						new DeclList(deAST, new EmptyDeclList(dummyPos), dlPos);
				} 
			} else {
				boolean global = true;
				dlAST = parseVarDecl(typeAST, idAST, global);
				finish(dlPos);
				if (currentToken.kind != Token.EOF) {
					DeclList referenceList = (DeclList) dlAST;
					while (!(referenceList.DL instanceof EmptyDeclList)) {
						referenceList = (DeclList) referenceList.DL;
					}
					referenceList.DL =  parseCompoundDeclList();
				}
			}
		}

		if (dlAST == null) {
			dlAST = new EmptyDeclList(dummyPos);
		}

		return dlAST;
	}

	/** for func declaration becase it start with type and id which
	 *  are same as variable decaration so we need to convey them..
	 */
	Decl parseFuncDecl(Type tAST, Ident idAST) throws SyntaxError {

		Decl fAST = null; 
		
		SourcePosition funcPos = new SourcePosition();
		start(funcPos);
		funcPos.charStart = tAST.position.charStart;
		funcPos.lineStart = tAST.position.lineStart;
		List fplAST = parseParaList();
		Stmt cAST = parseCompoundStmt();
		finish(funcPos);
		fAST = new FuncDecl(tAST, idAST, fplAST, cAST, funcPos);
		return fAST;
	}

	/*********************************** */
	/* variable decl parse nonterminals */
	/*********************************** */

	List parseVarDecl(Type tAST, Ident idAST, Boolean global) throws SyntaxError {
		
		List varDecl = null;
		varDecl = parseInitDeclaratorList(tAST, idAST, global);
		match(Token.SEMICOLON);
		return varDecl;
	}

	List parseInitDeclaratorList(Type tAST, Ident idAST, Boolean global)
			throws SyntaxError {

		DeclList decList = null;
		Decl firstInitDecl = null;
		SourcePosition firstInitDeclPos = new SourcePosition();
		start(firstInitDeclPos);
		firstInitDecl = parseInitDeclarator(tAST, idAST, global);
		if (currentToken.kind == Token.COMMA) {
			accept();
			Ident nextID = parseIdent();
			List initDeclList = parseInitDeclaratorList(tAST, nextID, global);
			finish(firstInitDeclPos);
			decList = new DeclList(firstInitDecl, initDeclList, firstInitDeclPos);
		} else {
			finish(firstInitDeclPos);
			decList = 
				new DeclList(firstInitDecl, new EmptyDeclList(dummyPos), firstInitDeclPos);
		}
		return decList;
	}

	Decl parseInitDeclarator(Type tAST, Ident idAST, Boolean global)
			throws SyntaxError {

		Decl initDecl = null;
		SourcePosition initDeclPos = new SourcePosition();
		start(initDeclPos);
		copyStart(idAST.position, initDeclPos);
		Type declType = parseDeclarator(tAST, idAST);
		if (currentToken.kind == Token.EQ) {
			accept();
			Expr initExpr = parseInitialiser();
			finish(initDeclPos);
			if (global) {
				initDecl = new GlobalVarDecl(declType, idAST, initExpr, initDeclPos);
			} else {
				initDecl = new LocalVarDecl(declType, idAST, initExpr, initDeclPos);
			}
		} else {
			finish(initDeclPos);
			if (global) {
				initDecl = 
					new GlobalVarDecl(declType, idAST, new EmptyExpr(dummyPos), initDeclPos);
			} else {
				initDecl =
					new LocalVarDecl(declType, idAST, new EmptyExpr(dummyPos), initDeclPos);
			}
		}
		return initDecl;
	}

	/** This parse declarator may parse two cases:
	 *  1. declarator -> identifier
	 *  2. declarator -> identifier "[" INTLITERAL? "]"
	 *  for case 2 it's defined as ArrayType
	 */
	Type parseDeclarator(Type tAST, Ident idAST) throws SyntaxError {

		Type typeAST = null;
		SourcePosition delPos = new SourcePosition();
		start(delPos);
		if (currentToken.kind == Token.LBRACKET) {
			accept();
			Expr intExpr = null;
			if (currentToken.kind == Token.INTLITERAL) {
				IntLiteral intLiteral = parseIntLiteral();
				intExpr = new IntExpr(intLiteral, previousTokenPosition);
			} else {
				intExpr = new EmptyExpr(dummyPos);
			}
			finish(delPos);
			match(Token.RBRACKET);
			typeAST = new ArrayType(tAST, intExpr, delPos);
		} else {
			typeAST = tAST;// basic variable declaration
			finish(delPos);
		}
		return typeAST;
	}

	Expr parseInitialiser() throws SyntaxError {

		Expr initAST = null;
		SourcePosition initPos = new SourcePosition();
		start(initPos);

		if (currentToken.kind == Token.LCURLY) {
			accept();
			List initExprList = parseInitialiserExprList();
			match(Token.RCURLY);
			finish(initPos);
			initAST = new InitExpr(initExprList, initPos);
		} else {
			initAST = parseExpr();
			finish(initPos);
		}
		return initAST;
	}

	List parseInitialiserExprList() throws SyntaxError {

		List exprList = null;
		SourcePosition exprListpos = new SourcePosition();
		start(exprListpos);
		Expr firstExprAST = parseExpr();
		if (currentToken.kind == Token.COMMA) {
			accept();
			List exprListDeep = parseInitialiserExprList();
			finish(exprListpos);
			exprList = new ExprList(firstExprAST, exprListDeep, exprListpos);
		} else {// reach the border
			finish(exprListpos);
			exprList = // define a empty expression list
				new ExprList(firstExprAST, new EmptyExprList(dummyPos), exprListpos);
		}

		return exprList;
	}

//  ======================== TYPES ==========================

	Type parseType() throws SyntaxError {

		Type typeAST = null;
		switch (currentToken.kind) {
			case Token.VOID:
				accept(); typeAST = new VoidType(previousTokenPosition);
				break;
			case Token.INT:
				accept(); typeAST = new IntType(previousTokenPosition);  
				break;
			case Token.FLOAT:
				accept(); typeAST = new FloatType(previousTokenPosition);
				break;
			case Token.BOOLEAN:
				accept(); typeAST = new BooleanType(previousTokenPosition); 
				break;
			default:
				syntacticError("\"%\" wrong result type for a function",
					currentToken.spelling);
				break;	
		}

		return typeAST;
	}

// ======================= STATEMENTS ==============================

	Stmt parseCompoundStmt() throws SyntaxError {
		Stmt cAST = null; 

		SourcePosition compoundStmtPos = new SourcePosition();
		start(compoundStmtPos);

		match(Token.LCURLY);

		// Insert code here to build a DeclList node for variable declarations
		List vlAST = parseVarDecList();
		List slAST = parseStmtList();
		match(Token.RCURLY);
		finish(compoundStmtPos);

		/* In the subset of the VC grammar, no variable declarations are
		 * allowed. Therefore, a block is empty iff it has no statements.
		 */
		if (slAST instanceof EmptyStmtList && vlAST instanceof EmptyDeclList) 
			cAST = new EmptyCompStmt(compoundStmtPos);
		else
			cAST = new CompoundStmt(vlAST, slAST, compoundStmtPos);
		return cAST;
	}

	List parseVarDecList() throws SyntaxError {
		List varList = null;
		SourcePosition varListPos = new SourcePosition();
		start(varListPos);

		if (currentToken.kind == Token.VOID	
			|| currentToken.kind == Token.INT
			|| currentToken.kind == Token.FLOAT
			|| currentToken.kind == Token.BOOLEAN) {
			
			Type tAST = parseType();
			Ident idAST = parseIdent();
			Boolean global = false;
			varList = parseVarDecl(tAST, idAST, global);
			DeclList varListReference = (DeclList) varList;

			if (currentToken.kind == Token.VOID	
				|| currentToken.kind == Token.INT
				|| currentToken.kind == Token.FLOAT
				|| currentToken.kind == Token.BOOLEAN) {
			
				while(!(varListReference.DL instanceof EmptyDeclList)) {
					varListReference = (DeclList) varListReference.DL;
				}
				varListReference.DL = parseVarDecList();
			}
		} else {
			finish(varListPos);
			varList = new EmptyDeclList(dummyPos);
		}
		return varList;
	}

	List parseStmtList() throws SyntaxError {

		List stmtList = new EmptyStmtList(dummyPos);
		SourcePosition stmtListPos = new SourcePosition();
		start(stmtListPos);

		if (currentToken.kind != Token.RCURLY) {
			Stmt stmtAST = parseStmt();
			if (currentToken.kind != Token.RCURLY) {
				stmtList = parseStmtList();
			}
			finish(stmtListPos);
			stmtList = new StmtList(stmtAST, stmtList, stmtListPos);
		}

		return stmtList;
	}

	Stmt parseStmt() throws SyntaxError {

		Stmt stmtAST = null;
		switch (currentToken.kind) {

			case Token.CONTINUE:
				stmtAST = parseContinueStmt();
	  			break;
			case Token.IF:
				stmtAST = parseIfStmt();
				break;
			case Token.FOR:
				stmtAST = parseForStmt();
				break;
			case Token.WHILE:
				stmtAST = parseWhileStmt();
				break;
			case Token.BREAK:
				stmtAST = parseBreakStmt();
				break;
			case Token.RETURN:
				stmtAST = parseReturnStmt();
				break;
			case Token.LCURLY:
				stmtAST = parseCompoundStmt();
				break;
			default:
				stmtAST =parseExprStmt();
	  			break;
		}

		return stmtAST;
	}

	Stmt parseContinueStmt() throws SyntaxError {

		Stmt conAST = null;
		SourcePosition conPos = new SourcePosition();
		start(conPos);
		match(Token.CONTINUE);
		match(Token.SEMICOLON);
		finish(conPos);
		conAST = new ContinueStmt(conPos);

		return conAST;
	}

	Stmt parseIfStmt() throws SyntaxError {
		
		Stmt ifAST = null;
		SourcePosition ifPos = new SourcePosition();
		start(ifPos);
		match(Token.IF);
		match(Token.LPAREN);
		Expr eAST = parseExpr();
		match(Token.RPAREN);
		Stmt s1AST = parseStmt();
		if (currentToken.kind == Token.ELSE) {
			accept();
			finish(ifPos);
			Stmt s2AST = parseStmt();
			ifAST = new IfStmt(eAST, s1AST, s2AST, ifPos);
		} else {
			finish(ifPos);
			ifAST = new IfStmt(eAST, s1AST, ifPos);
		}

		return ifAST;

	}

	Stmt parseForStmt() throws SyntaxError {

		Stmt forStmt = null;
		SourcePosition forPos = new SourcePosition();
		start(forPos);

		match(Token.FOR);
		match(Token.LPAREN);

		/* match three expression in the for loop we need to know whether is there
		*  a expr or not, calculate the First() set for expr
		*  First(expr) = { +, -, !, (, ID, INITLITERAL, FLOATLITERAL, BOOLLITERAL,
		*  STRINGLITERAL }
		*/

		/**
		 * For the three expressions, they may not exist say they should be EmptyExpr
		 * So firstly we can initialize them as EmptyExpr, if they exists and then we
		 * can update them as the real expr;
		 */
		Expr expr1 = new EmptyExpr(dummyPos);
		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			expr1 = parseExpr();
		}
		match(Token.SEMICOLON);

		Expr expr2 = new EmptyExpr(dummyPos);
		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			expr2 = parseExpr();
		}
		match(Token.SEMICOLON);

		Expr expr3 = new EmptyExpr(dummyPos);
		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			expr3 = parseExpr();
		}
		match(Token.RPAREN);
		Stmt stAST = parseStmt();
		finish(forPos);
		forStmt = new ForStmt(expr1, expr2, expr3, stAST, forPos);

		return forStmt;
	}

	Stmt parseWhileStmt() throws SyntaxError {

		Stmt whileAST = null;
		SourcePosition whilePos = new SourcePosition();
		start(whilePos);
		match(Token.WHILE);
		match(Token.LPAREN);
		Expr expr = parseExpr();
		match(Token.RPAREN);
		Stmt sAST = parseStmt();
		finish(whilePos);
		whileAST = new WhileStmt(expr, sAST, whilePos);

		return whileAST;
	}

	Stmt parseBreakStmt() throws SyntaxError {
		
		Stmt breakAST = null;
		SourcePosition breakPos = new SourcePosition();
		start(breakPos);
		match(Token.BREAK);
		match(Token.SEMICOLON);
		finish(breakPos);
		breakAST = new BreakStmt(breakPos);

		return breakAST;
	}

	Stmt  parseReturnStmt() throws SyntaxError {
		
		Stmt returnAST = null;
		SourcePosition returnPos = new SourcePosition();
		start(returnPos);
		match(Token.RETURN);
		Expr expr = new EmptyExpr(dummyPos);
		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			expr = parseExpr();
		}
		match(Token.SEMICOLON);
		finish(returnPos);
		returnAST = new ReturnStmt(expr, returnPos);

		return returnAST;
	}

	Stmt parseExprStmt() throws SyntaxError {

		Stmt sAST = null;
		SourcePosition stmtPos = new SourcePosition();
		start(stmtPos);
		Expr expr = new EmptyExpr(dummyPos);
		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {
			
			expr = parseExpr();

		}
		match(Token.SEMICOLON);
		finish(stmtPos);
		sAST = new ExprStmt(expr, stmtPos);

		return sAST;
  	}

// ======================= EXPRESSIONS ======================

	Expr parseExpr() throws SyntaxError {

		Expr exprAST = null;
		exprAST = parseAssignExpr();
		return exprAST;
	}

	Expr parseAssignExpr() throws SyntaxError {

		Expr assExpr = null;
		SourcePosition assPos = new SourcePosition();
		start(assPos);

		assExpr = parseCondOrExpr();
		if (currentToken.kind == Token.EQ) {
			acceptOperator();
			Expr nextAss = parseAssignExpr();
			finish(assPos);
			assExpr = new AssignExpr(assExpr, nextAss, assPos);
		}

		return assExpr;
	}

	Expr parseCondOrExpr() throws SyntaxError {

		Expr condOrExpr = null;
		SourcePosition condOrExprPos = new SourcePosition();
		start(condOrExprPos);

		condOrExpr = parseCondAndExpr();
		if (currentToken.kind == Token.OROR) {
			Operator o =  acceptOperator();
			Expr expr2 = parseCondOrExpr();
			finish(condOrExprPos);
			condOrExpr = new BinaryExpr(condOrExpr, o, expr2, condOrExprPos);
		}
		
		return condOrExpr;
	}

	Expr parseCondAndExpr() throws SyntaxError {

		Expr condAndExpr = null;
		SourcePosition condAndExprPos = new SourcePosition();
		start(condAndExprPos);

		condAndExpr = parseEqualityExpr();
		while (currentToken.kind == Token.ANDAND) {
			Operator o = acceptOperator();
			Expr expr2 = parseEqualityExpr();

			SourcePosition addPos = new SourcePosition();
			copyStart(condAndExprPos, addPos);
			finish(addPos);
			condAndExpr = new BinaryExpr(condAndExpr, o, expr2, addPos);
		}

		return condAndExpr;
	}

	Expr parseEqualityExpr() throws SyntaxError {

		Expr equalityExpr = null;
		SourcePosition equalityExprPos = new SourcePosition();
		start(equalityExprPos);

		equalityExpr = parseRelExpr();
		while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
			Operator o = acceptOperator();
			Expr expr2 = parseRelExpr();

			SourcePosition addPos = new SourcePosition();
			copyStart(equalityExprPos, addPos);
			finish(addPos);
			equalityExpr = new BinaryExpr(equalityExpr, o, expr2, addPos);
		}

		return equalityExpr;
	}

	Expr parseRelExpr() throws SyntaxError {

		Expr relExpr = null;
		SourcePosition relExprPos = new SourcePosition();
		start(relExprPos);

		relExpr = parseAdditiveExpr();
		while (
			currentToken.kind == Token.LT || 
			currentToken.kind == Token.LTEQ || 
			currentToken.kind == Token.GT || 
			currentToken.kind == Token.GTEQ) {
			
			Operator o = acceptOperator();
			Expr expr2 = parseAdditiveExpr();

			SourcePosition addPos = new SourcePosition();
			copyStart(relExprPos, addPos);
			finish(addPos);
			relExpr = new BinaryExpr(relExpr, o, expr2, addPos);
		}

		return relExpr;
	}

	Expr parseAdditiveExpr() throws SyntaxError {

		Expr exprAST = null;
		SourcePosition addStartPos = new SourcePosition();
		start(addStartPos);

		exprAST = parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS
				|| currentToken.kind == Token.MINUS) {
			Operator opAST = acceptOperator();
			Expr e2AST = parseMultiplicativeExpr();

			SourcePosition addPos = new SourcePosition();
			copyStart(addStartPos, addPos);
			finish(addPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, addPos);
		}
		
		return exprAST;
	}

	Expr parseMultiplicativeExpr() throws SyntaxError {

		Expr exprAST = null;
		SourcePosition multStartPos = new SourcePosition();
		start(multStartPos);

		exprAST = parseUnaryExpr();
		while (currentToken.kind == Token.MULT
				|| currentToken.kind == Token.DIV) {
			Operator opAST = acceptOperator();
			Expr e2AST = parseUnaryExpr();
			SourcePosition multPos = new SourcePosition();
			copyStart(multStartPos, multPos);
			finish(multPos);
			exprAST = new BinaryExpr(exprAST, opAST, e2AST, multPos);
		}

		return exprAST;
	}

	Expr parseUnaryExpr() throws SyntaxError {

		Expr exprAST = null;
		SourcePosition unaryPos = new SourcePosition();
		start(unaryPos);

		switch (currentToken.kind) {
			case Token.MINUS: case Token.PLUS: case Token.NOT:
				Operator opAST = acceptOperator();
				Expr e2AST = parseUnaryExpr();
				finish(unaryPos);
				exprAST = new UnaryExpr(opAST, e2AST, unaryPos);
				break;
			default:
				exprAST = parsePrimaryExpr();
				break; 
		}

		return exprAST;
	}

	Expr parsePrimaryExpr() throws SyntaxError {

		Expr exprAST = null;

		SourcePosition primPos = new SourcePosition();
		start(primPos);

		switch (currentToken.kind) {

			case Token.ID:
				Ident iAST = parseIdent();
				if (currentToken.kind == Token.LPAREN) {
					List agList = parseArgList();
					finish(primPos);
					exprAST = new CallExpr(iAST, agList, primPos);
				} else if (currentToken.kind == Token.LBRACKET) {
					accept();
					Var spVar = new SimpleVar(iAST, previousTokenPosition);
					Expr exprInside = parseExpr();
					finish(primPos);
					match(Token.RBRACKET);
					exprAST = new ArrayExpr(spVar, exprInside, primPos);
				} else {
					finish(primPos);
					Var spVar = new SimpleVar(iAST, primPos);
					exprAST = new VarExpr(spVar, primPos);
				}
				break;
			case Token.LPAREN:
				accept();
				exprAST = parseExpr();
				match(Token.RPAREN);
				break;
			case Token.INTLITERAL:
				IntLiteral ilAST = parseIntLiteral();
				finish(primPos);
				exprAST = new IntExpr(ilAST, primPos);
				break;
			case Token.FLOATLITERAL:
				FloatLiteral flAST = parseFloatLiteral();
				finish(primPos);
				exprAST = new FloatExpr(flAST, primPos);
				break;
			case Token.BOOLEANLITERAL:
				BooleanLiteral blAST = parseBooleanLiteral();
				finish(primPos);
				exprAST = new BooleanExpr(blAST, primPos);
				break;
			case Token.STRINGLITERAL:
				StringLiteral slAST = parseStringLiteral();
				finish(primPos);
				exprAST = new StringExpr(slAST, primPos);
				break;
			default:
				syntacticError("illegal primary expression", currentToken.spelling);
				break;
		}

		return exprAST;
	}

// ========================== ID, OPERATOR and LITERALS ========================

	Ident parseIdent() throws SyntaxError {

		Ident I = null; 

		if (currentToken.kind == Token.ID) {
			String spelling = currentToken.spelling;
			accept();
			I = new Ident(spelling, previousTokenPosition);
		} else 
			syntacticError("identifier expected here", "");
		return I;
	}

// acceptOperator parses an operator, and constructs a leaf AST for it

	Operator acceptOperator() throws SyntaxError {
		Operator O = null;

		previousTokenPosition = currentToken.position;
		String spelling = currentToken.spelling;
		O = new Operator(spelling, previousTokenPosition);
		currentToken = scanner.getToken();
		return O;
	}


	IntLiteral parseIntLiteral() throws SyntaxError {
		IntLiteral IL = null;

		if (currentToken.kind == Token.INTLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			IL = new IntLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("integer literal expected here", "");
		return IL;
	}

	FloatLiteral parseFloatLiteral() throws SyntaxError {
		FloatLiteral FL = null;

		if (currentToken.kind == Token.FLOATLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			FL = new FloatLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("float literal expected here", "");
		return FL;
	}

	BooleanLiteral parseBooleanLiteral() throws SyntaxError {
		BooleanLiteral BL = null;

		if (currentToken.kind == Token.BOOLEANLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			BL = new BooleanLiteral(spelling, previousTokenPosition);
		} else 
			syntacticError("boolean literal expected here", "");
		return BL;
	}

	StringLiteral parseStringLiteral() throws SyntaxError {
		StringLiteral SL = null;

		if (currentToken.kind == Token.STRINGLITERAL) {
			String spelling = currentToken.spelling;
			accept();
			SL = new StringLiteral(spelling, previousTokenPosition);
		} else {
			syntacticError("string literal expected here", "");
		}
		return SL;
	}

	// ======================= PARAMETERS =======================

	List parseParaList() throws SyntaxError {

		List formalsAST = null;
		SourcePosition formalsPos = new SourcePosition();
		start(formalsPos);

		match(Token.LPAREN);
		if (currentToken.kind == Token.VOID
			|| currentToken.kind == Token.FLOAT
			|| currentToken.kind == Token.INT 
			|| currentToken.kind == Token.BOOLEAN) {
			formalsAST = parseProperParaList();
			match(Token.RPAREN);
			finish(formalsPos);
		} else {
			match(Token.RPAREN);
			finish(formalsPos);
			formalsAST = new EmptyParaList(formalsPos);
		}

		return formalsAST;
	}

	List parseProperParaList() throws SyntaxError {

		List paraList = null;
		SourcePosition paraListPos = new SourcePosition();
		start(paraListPos);

		ParaDecl paraDecl = parseParaDecl();
		if (currentToken.kind == Token.COMMA) {
			match(Token.COMMA);
			paraList = parseProperParaList();
			finish(paraListPos);
			paraList = new ParaList(paraDecl, paraList, paraListPos);
		} else {
			finish(paraListPos);
			paraList = new EmptyParaList(paraListPos);
			paraList = new ParaList(paraDecl, paraList, paraListPos);
		}

		return paraList;
	}

	ParaDecl parseParaDecl() throws SyntaxError {

		ParaDecl PD = null;
		SourcePosition pdASTPos = new SourcePosition();
		start(pdASTPos);

		Type typAST = parseType();
		Ident idAST = parseIdent();
		Type deAST = parseDeclarator(typAST, idAST);

		finish(pdASTPos);
		PD = new ParaDecl(deAST, idAST, pdASTPos);
		return PD;
	}

	List parseArgList() throws SyntaxError {

		List argList = null;
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);
		match(Token.LPAREN);

		if (currentToken.kind == Token.ID 
			|| currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS 
			|| currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN 
			|| currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL 
			|| currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {
			
			argList = parseProArgList();
		} else {
			finish(argListPos);
			argList = new EmptyArgList(argListPos);
		}
		match(Token.RPAREN);

		return argList;
	}

	List parseProArgList() throws SyntaxError {
		List argList = null;
		SourcePosition argListPos = new SourcePosition();
		start(argListPos);

		Arg arg1 = parseArg();
		List nextArg = new EmptyArgList(dummyPos);
		if (currentToken.kind == Token.COMMA) {
			accept();
			nextArg = parseProArgList();
		}
		finish(argListPos);
		argList = new ArgList(arg1, nextArg, argListPos);

		return argList;
	}

	Arg parseArg() throws SyntaxError {

		Arg arg = null;
		SourcePosition argPos = new SourcePosition();
		start(argPos);
		Expr expr = parseExpr();
		finish(argPos);
		arg = new Arg(expr, argPos);

		return arg;
	}
}
