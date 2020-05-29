/***
 * *
 * * Recogniser.java            
 * *
 ***/

/* At this stage, this parser accepts a subset of VC defined	by
 * the following grammar. 
 *
 * You need to modify the supplied parsing methods (if necessary) and 
 * add the missing ones to obtain a parser for the VC language.
 *
 * (27---Feb---2019)

program       -> func-decl

// declaration

func-decl     -> void identifier "(" ")" compound-stmt

identifier    -> ID

// statements 
compound-stmt -> "{" stmt* "}" 
stmt          -> continue-stmt
			  |  expr-stmt
continue-stmt -> continue ";"
expr-stmt     -> expr? ";"

// expressions 
expr                -> assignment-expr
assignment-expr     -> additive-expr
additive-expr       -> multiplicative-expr
					|  additive-expr "+" multiplicative-expr
multiplicative-expr -> unary-expr
				|  multiplicative-expr "*" unary-expr
unary-expr          -> "-" unary-expr
			|  primary-expr

primary-expr        -> identifier
 			|  INTLITERAL
			| "(" expr ")"
*/

package VC.Recogniser;

import VC.Scanner.Scanner;
import VC.Scanner.SourcePosition;
import VC.Scanner.Token;
import VC.ErrorReporter;

public class Recogniser {

  	private Scanner scanner;
  	private ErrorReporter errorReporter;
  	private Token currentToken;

  	public Recogniser (Scanner lexer, ErrorReporter reporter) {
		scanner = lexer;
		errorReporter = reporter;

		currentToken = scanner.getToken();
  	}

// match checks to see f the current token matches tokenExpected.
// If so, fetches the next token.
// If not, reports a syntactic error.

	void match(int tokenExpected) throws SyntaxError {
		if (currentToken.kind == tokenExpected) {
			currentToken = scanner.getToken();
		} else {
	  		syntacticError("\"%\" expected here", Token.spell(tokenExpected));
		}
  	}

 // accepts the current token and fetches the next
  	void accept() {
		currentToken = scanner.getToken();
  	}

  void syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
	SourcePosition pos = currentToken.position;
	errorReporter.reportError(messageTemplate, tokenQuoted, pos);
	throw(new SyntaxError());
  }


// ========================== PROGRAMS ========================
	  
  	/**
	   * parse program, there are two cases for program func and variables 
	   * but the type and identifier cannot help to differentiate them need
	   * to see the third token is LPAREN or not. There should have a loop 
	   * which help parse all of the funcs and variables until we meet $
	   */
	public void parseProgram() {

		try {
	  		while (currentToken.kind != Token.EOF) {
				/* For first two tokens it should be datatype and id */
				parseDataType();
				parseIdent();
				if (currentToken.kind == Token.LPAREN) {
					parseFuncDecl();
				} else {
					parseVarDecl();
				}
			}

			/* the last token should be a EOF */
	  		if (currentToken.kind != Token.EOF) {
				syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
	  		}
		}
		catch (SyntaxError s) {  }
  	}

// ========================== DATATYPES ========================

	/* There are totally 4 datatypes, void, int, float, boolean */

	void parseDataType() throws SyntaxError {
		switch (currentToken.kind) {
			case Token.VOID:
				accept(); break;
			case Token.INT:
				accept(); break;
			case Token.FLOAT:
				accept(); break;
			case Token.BOOLEAN:
				accept(); break;
			default:
				syntacticError("\"%\" wrong result type for a function", currentToken.spelling);
				break;	
		}
	}

// ======================= IDENTIFIERS ======================

 // Call parseIdent rather than match(Token.ID). 
 // In Assignment 3, an Identifier node will be constructed in here.

 	void parseIdent() throws SyntaxError {

		if (currentToken.kind == Token.ID) {
		  	accept();
		} else {
			syntacticError("identifier expected here", "");
		}
  	}

// ========================== DECLARATIONS ========================

	void parseFuncDecl() throws SyntaxError {

		// parseDataType();
		// parseIdent();
		parseParaList();
		parseCompoundStmt();

	}
	
	/*********************************** */
	/* variable decl parse nonterminals */
	/*********************************** */

	void parseVarDecl() throws SyntaxError {
		
		parseInitDeclaratorList();
		match(Token.SEMICOLON);
	}

	void parseInitDeclaratorList() throws SyntaxError {
		parseInitDeclarator();
		while (currentToken.kind == Token.COMMA) {
			accept();
			parseIdent();
			parseInitDeclarator();
		}
	}

	void parseInitDeclarator() throws SyntaxError {
		parseDeclarator();
		if (currentToken.kind == Token.EQ) {
			acceptOperator();
			parseInitialiser();
		}
	}

	void parseDeclarator() throws SyntaxError {
		if (currentToken.kind == Token.LBRACKET) {
			accept();
			if (currentToken.kind == Token.INTLITERAL) {
				accept();
			} 
			match(Token.RBRACKET);
		}
	}

	void parseDeclaratorForPara() throws SyntaxError {
		parseIdent(); /* may be some problems will happen here */
		if (currentToken.kind == Token.LBRACKET) {
			accept();
			if (currentToken.kind == Token.INTLITERAL) {
				accept();
			} 
			match(Token.RBRACKET);
		}
	}

	void parseInitialiser() throws SyntaxError {
		if (currentToken.kind == Token.LCURLY) {
			accept();
			parseExpr();
			while (currentToken.kind == Token.COMMA) {
				accept();
				parseExpr();
			}
			match(Token.RCURLY);
		} else {
			parseExpr();
		}
	}

// ======================= STATEMENTS ==============================

  	void parseCompoundStmt() throws SyntaxError {

		match(Token.LCURLY);
		parseVarDeclList();
		parseStmtList();
		match(Token.RCURLY);
	}
	  
	void parseVarDeclList() throws SyntaxError {
		while (currentToken.kind == Token.INT
				|| currentToken.kind == Token.FLOAT
				|| currentToken.kind == Token.VOID
				|| currentToken.kind == Token.BOOLEAN) {
			parseDataType();
			parseIdent();
			parseVarDecl();
		}
	}

 // Here, a new nontermial has been introduced to define { stmt } *
  	void parseStmtList() throws SyntaxError {

		while (currentToken.kind != Token.RCURLY) 
	  		parseStmt();
  	}

  	void parseStmt() throws SyntaxError {

		switch (currentToken.kind) {

			case Token.CONTINUE:
	  			parseContinueStmt();
	  			break;
			case Token.IF:
				parseIfStmt();
				break;
			case Token.FOR:
				parseForStmt();
				break;
			case Token.WHILE:
				parseWhileStmt();
				break;
			case Token.BREAK:
				parseBreakStmt();
				break;
			case Token.RETURN:
				parseReturnStmt();
				break;
			case Token.LCURLY:
				parseCompoundStmt();
				break;
			default:
	  			parseExprStmt();
	  			break;
		}
	}
	 
	void parseIfStmt() throws SyntaxError {
		match(Token.IF);
		match(Token.LPAREN);
		parseExpr();
		match(Token.RPAREN);
		parseStmt();
		if (currentToken.kind == Token.ELSE) {
			accept();
			parseStmt();
		}
	}

	void parseForStmt() throws SyntaxError {
		match(Token.FOR);
		match(Token.LPAREN);

		/* match three expression in the for loop we need to know whether is there
		*  a expr or not, calculate the First() set for expr
		*  First(expr) = { +, -, !, (, ID, INITLITERAL, FLOATLITERAL, BOOLLITERAL, STRINGLITERAL }
		*/

		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			parseExpr();
		}
		match(Token.SEMICOLON);

		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
		|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
		|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
		|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
		|| currentToken.kind == Token.STRINGLITERAL) {

			parseExpr();
		}
		match(Token.SEMICOLON);

		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			parseExpr();
		}
		match(Token.RPAREN);
		parseStmt();
	}

	void parseWhileStmt() throws SyntaxError {
		match(Token.WHILE);
		match(Token.LPAREN);
		parseExpr();
		match(Token.RPAREN);
		parseStmt();
	}

	void parseBreakStmt() throws SyntaxError {
		match(Token.BREAK);
		match(Token.SEMICOLON);
	}

  	void parseContinueStmt() throws SyntaxError {
		match(Token.CONTINUE);
		match(Token.SEMICOLON);
	}

	void parseReturnStmt() throws SyntaxError {
		match(Token.RETURN);
		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			parseExpr();
		}
		match(Token.SEMICOLON);
	}
	
  	void parseExprStmt() throws SyntaxError {

		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			parseExpr();
		}
		match(Token.SEMICOLON);
  	}

// ======================= OPERATORS ======================

 // Call acceptOperator rather than accept(). 
 // In Assignment 3, an Operator Node will be constructed in here.

  void acceptOperator() throws SyntaxError {

	currentToken = scanner.getToken();
  }


// ======================= EXPRESSIONS ======================

  	void parseExpr() throws SyntaxError {
		parseAssignExpr();
  	}

  	void parseAssignExpr() throws SyntaxError {
		parseCondOrExpr();
		while (currentToken.kind == Token.EQ) {
			acceptOperator();
			parseCondOrExpr();
		}
	}

	void parseCondOrExpr() throws SyntaxError {
		parseCondAndExpr();
		while (currentToken.kind == Token.OROR) {
			acceptOperator();
			parseCondAndExpr();
		}
	}

	void parseCondAndExpr() throws SyntaxError {
		parseEqualityExpr();
		while (currentToken.kind == Token.ANDAND) {
			acceptOperator();
			parseEqualityExpr();
		}
	}

	void parseEqualityExpr() throws SyntaxError {
		parseRelExpr();
		while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
			acceptOperator();
			parseRelExpr();
		}
	}

	void parseRelExpr() throws SyntaxError {
		parseAdditiveExpr();
		while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ
				|| currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
			acceptOperator();
			parseAdditiveExpr();
		}
	}

  	void parseAdditiveExpr() throws SyntaxError {
		parseMultiplicativeExpr();
		while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
	  		acceptOperator();
	  		parseMultiplicativeExpr();
		}
  	}

  	void parseMultiplicativeExpr() throws SyntaxError {

		parseUnaryExpr();
		while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV) {
	  		acceptOperator();
	  		parseUnaryExpr();
		}
  	}

  	void parseUnaryExpr() throws SyntaxError {

		switch (currentToken.kind) {
	  		case Token.MINUS:
		  		acceptOperator();
		  		parseUnaryExpr();
				break;
			case Token.PLUS:
				acceptOperator();
				parseUnaryExpr();
				break;
			case Token.NOT:
				acceptOperator();
				parseUnaryExpr();
				break;
	  		default:
				parsePrimaryExpr();
				break;	   
		}
  	}

  	void parsePrimaryExpr() throws SyntaxError {

		switch (currentToken.kind) {
	  		case Token.ID:
				parseIdent();
				if (currentToken.kind == Token.LPAREN) {
					parseArgList();
				} else if (currentToken.kind == Token.LBRACKET) {
					accept();
					parseExpr();
					match(Token.RBRACKET);
				}
				break;
			case Token.LPAREN:
				accept();
		 		parseExpr();
	 		 	match(Token.RPAREN);
				break;
	  		case Token.INTLITERAL:
			  	accept();
				break;
			case Token.FLOATLITERAL:
				accept();
				break;
			case Token.BOOLEANLITERAL:
				accept();
				break;
			case Token.STRINGLITERAL:
				accept();
				break;
			default:
				syntacticError("illegal parimary expression", currentToken.spelling);
		}
  	}

// ========================== LITERALS ========================

  // Call these methods rather than accept().  In Assignment 3, 
  // literal AST nodes will be constructed inside these methods. 

  	void parseIntLiteral() throws SyntaxError {
		if (currentToken.kind == Token.INTLITERAL) {
	  		accept();
		} else{
			syntacticError("integer literal expected here", "");
		}
  	}

  	void parseFloatLiteral() throws SyntaxError {
		if (currentToken.kind == Token.FLOATLITERAL) {
			accept();
		} else {
			syntacticError("float literal expected here", "");
		}
  	}

  	void parseBooleanLiteral() throws SyntaxError {
		if (currentToken.kind == Token.BOOLEANLITERAL) {
			accept();
		} else {
			syntacticError("boolean literal expected here", "");
		}
  	}

  	void parseStringLiteral() throws SyntaxError {
		if (currentToken.kind == Token.STRINGLITERAL) {
			accept();
		} else {
			syntacticError("string literal expected here", "");
		}
  	}

  // ========================== PARAMETERS ========================
	
	void parseParaList() throws SyntaxError {
		match(Token.LPAREN);
		if (currentToken.kind == Token.VOID || currentToken.kind == Token.FLOAT
			|| currentToken.kind == Token.INT || currentToken.kind == Token.BOOLEAN) {
			parseProperParaList();
		}
		match(Token.RPAREN);
	}

	void parseProperParaList() throws SyntaxError {
		parseParaDecl();
		while (currentToken.kind == Token.COMMA) {
			match(Token.COMMA);
			parseParaDecl();
		}
	}

	void parseParaDecl() throws SyntaxError {
		parseDataType();
		parseDeclaratorForPara();
	}

	void parseArgList() throws SyntaxError {
		match(Token.LPAREN);
		if (currentToken.kind == Token.ID || currentToken.kind == Token.PLUS
			|| currentToken.kind == Token.MINUS || currentToken.kind == Token.NOT
			|| currentToken.kind == Token.LPAREN || currentToken.kind == Token.INTLITERAL
			|| currentToken.kind == Token.FLOATLITERAL || currentToken.kind == Token.BOOLEANLITERAL
			|| currentToken.kind == Token.STRINGLITERAL) {

			parseProArgList();
		}
		match(Token.RPAREN);
	}

	void parseProArgList() throws SyntaxError {
		parseArg();
		while (currentToken.kind == Token.COMMA) {
			accept();
			parseArg();
		}
	}

	void parseArg() throws SyntaxError {
		parseExpr();
	}
}
