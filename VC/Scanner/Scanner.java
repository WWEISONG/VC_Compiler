/**
 **	Scanner.java                        
 **/

package VC.Scanner;

import VC.ErrorReporter;
import java.lang.Character;

public final class Scanner { 

	private SourceFile sourceFile;
	private boolean debug;

	private ErrorReporter errorReporter;
	private StringBuffer currentSpelling;
	private char currentChar;
	private SourcePosition sourcePos;

	private int curLine, finishLine, curColumnStart, curColumnEnd;

// =========================================================

	public Scanner(SourceFile source, ErrorReporter reporter) {
		sourceFile = source;
		errorReporter = reporter;
		currentChar = sourceFile.getNextChar();
		debug = false;

		curLine = 1;
		finishLine = 1;
		curColumnStart = 1;
		curColumnEnd = 1;
	}

	public void enableDebugging() {

		debug = true;
	}

  	// accept gets the next character from the source program.

	private void accept() {

		currentSpelling.append(currentChar);
		currentChar = sourceFile.getNextChar();
		curColumnEnd += 1;
	}

	private void escapeAccept(char eschar) {
		currentSpelling.append(eschar);
		currentChar = sourceFile.getNextChar();
		currentChar = sourceFile.getNextChar();
		curColumnEnd += 2;
	}
	  

		// inspectChar returns the n-th character after currentChar
		// in the input stream. 
		//
		// If there are fewer than nthChar characters between currentChar 
		// and the end of file marker, SourceFile.eof is returned.
		// 
		// Both currentChar and the current position in the input stream
		// are *not* changed. Therefore, a subsequent call to accept()
		// will always return the next char after currentChar.

  	private char inspectChar(int nthChar) {

		return sourceFile.inspectChar(nthChar);
  	}

  	private int nextToken() {
  	// Tokens: separators, operators, literals, identifiers and keyworods
	   
    	switch (currentChar) {
			// separators, we only focus on 4 types of errors, for single
			// separator there will be no error appearing
			case '{': accept(); return Token.LCURLY;
			case '}': accept(); return Token.RCURLY;
			case '(': accept(); return Token.LPAREN;
			case ')': accept(); return Token.RPAREN;
			case '[': accept(); return Token.LBRACKET;
			case ']': accept(); return Token.RBRACKET;
			case ';': accept(); return Token.SEMICOLON;
			case ',': accept(); return Token.COMMA;
			
			// operators
			case '+': accept(); return Token.PLUS;
			case '-': accept();	return Token.MINUS;
			case '*': accept(); return Token.MULT;
			case '/': accept(); return Token.DIV;
			case '!':
				char nextChar = inspectChar(1);
				if (nextChar == '=') { // if current char is '!', we need to check the next char
					accept();
					accept();
					return Token.NOTEQ;
				}else{
					accept();
					return Token.NOT;
				}
			case '=':
				char nextE = inspectChar(1);
				if (nextE == '=') {
					accept();
					accept();
					return Token.EQEQ;
				}else{
					accept();
					return Token.EQ;
				}
			case '<':
				if (inspectChar(1) == '=') {
					accept();
					accept();
					return Token.LTEQ;
				}else{
					accept();
					return Token.LT;
				}
			case '>':
				if (inspectChar(1) == '=') {
					accept();
					accept();
					return Token.GTEQ;
				}else{
					accept();
					return Token.GT;
				}
			case '&':
				if (inspectChar(1) == '&') {
					accept();
					accept();
					return Token.ANDAND;
				}else{
					accept();
					return Token.ERROR;
				}
			case '|':	
				accept();
				if (currentChar == '|') {
					accept();
					return Token.OROR;
				} else {
					return Token.ERROR;
				}

			// int-literal and float-literal, both of them will never be wrong
			// if you cannot match correctly, you should use longest match principle
			case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7': 
			case '8': case '9':
				accept();
				int dataType = 0;
				boolean longestMatch = false;

				while(!Character.isWhitespace(currentChar) && !longestMatch) {
					switch(currentChar) { // according to next char there are 5 cases
						case '0': case '1': case '2': case '3': case '4': case '5': 
						case '6': case '7': case '8': case '9':
							accept();
							break;
						case 'e': case 'E': // next char must be a number or operator
							switch(inspectChar(1)) {
								case '0': case '1': case '2': case '3': case '4': case '5': 
								case '6': case '7': case '8': case '9': 
									dataType = 1; 
									accept();
									break; 
								case '+': case '-':
									switch(inspectChar(2)) {
										case '0': case '1': case '2': case '3': case '4': case '5': 
										case '6': case '7': case '8': case '9': 
											dataType = 1; 
											accept();
											break;
										default: longestMatch = true; break;
									}
									break;
								default: longestMatch = true; break;
							}
							break;
						case '.':
							dataType = 1; 
							accept();
							break; 
						case '+': case '-': // check current datatype
							if (dataType == 0) {
								longestMatch = true;
							}else if (dataType == 1) {
								switch(inspectChar(1)) { // judge next char is a number or not
									case '0': case '1': case '2': case '3': case '4': case '5': 
									case '6': case '7': case '8': case '9':
										accept();
										break;
									default: longestMatch = true; break;
								}
							}
							break;						
						default: longestMatch = true; break;
					}
				}

				if (dataType == 0) {
					return Token.INTLITERAL;
				}else if (dataType == 1) {
					return Token.FLOATLITERAL;
				}

			// float-literal or ERROR case
			case '.':
				//  attempting to recognise a float
				accept();
				boolean isFloat = true, floatLongestMatch = false;

				switch(currentChar) { // check next char is a number
					case '0': case '1': case '2': case '3': case '4': case '5': 
					case '6': case '7': case '8': case '9':
						accept(); break;
					default: isFloat = false; break;
				}
				
				if (isFloat) {
					while (!Character.isWhitespace(currentChar) && !floatLongestMatch) {
						switch(currentChar) { // add the left chars
							case '0': case '1': case '2': case '3': case '4': case '5': 
							case '6': case '7': case '8': case '9':
								accept(); break;
							case 'e': case 'E':
								switch(inspectChar(1)) {
									case '0': case '1': case '2': case '3': case '4': case '5': 
									case '6': case '7': case '8': case '9':
										accept(); break;
									case '+': case '-':
										switch(inspectChar(2)) {
											case '0': case '1': case '2': case '3': case '4': case '5': 
											case '6': case '7': case '8': case '9':
												accept(); accept(); break;
											default: floatLongestMatch = true; break;
										}
										break;
									default: floatLongestMatch = true; break;
								}
								break;
							default: floatLongestMatch = true; break;	
						}
					}
					return Token.FLOATLITERAL;
				}else{
					return Token.ERROR;
				}
			// string case
			case '"':
				currentChar = sourceFile.getNextChar();
				curColumnEnd += 1;
				char escapeChar;
				boolean stringFinish = false;
				while (!stringFinish) {
					switch(currentChar) {
						// handle general escape characters
						case '\\':
							switch(inspectChar(1)) {
								case 'b': escapeChar = '\b'; escapeAccept(escapeChar); break;
								case 'f': escapeChar = '\f'; escapeAccept(escapeChar); break;
								case 'n': escapeChar = '\n'; escapeAccept(escapeChar); break;
								case 'r': escapeChar = '\r'; escapeAccept(escapeChar); break;
								case 't': escapeChar = '\t'; escapeAccept(escapeChar); break;
								case '\\': escapeChar = '\\'; escapeAccept(escapeChar);break;
								case '\'': escapeChar = '\''; escapeAccept(escapeChar);break;
								case '\"': escapeChar = '\"'; escapeAccept(escapeChar);break;
								default: 
									accept();
									String errorString = "\\";
									if (!Character.isWhitespace(currentChar) &&
										currentChar != SourceFile.eof){
										errorString = errorString + currentChar;
									}
									sourcePos.lineStart = curLine;
									sourcePos.lineFinish = curLine;
									sourcePos.charStart = curColumnStart;
									sourcePos.charFinish = curColumnEnd - 1;
									errorReporter.reportError(errorString+
											": illegal escape character", "", sourcePos);
									break;
							}
							break;
						case '\n':
							stringFinish = true;
							sourcePos.lineStart = curLine;
							sourcePos.lineFinish = curLine;
							sourcePos.charStart = curColumnStart;
							sourcePos.charFinish = curColumnStart;
							errorReporter.reportError(currentSpelling.toString()+
									": unterminated string", "", sourcePos);
							return Token.STRINGLITERAL;
						// handle end of "
						case '"':
							currentChar = sourceFile.getNextChar();
							curColumnEnd += 1;
							stringFinish = true;
							return Token.STRINGLITERAL;
						case SourceFile.eof:
							stringFinish = true;
							sourcePos.lineStart = curLine;
							sourcePos.lineFinish = curLine;
							sourcePos.charStart = curColumnStart;
							sourcePos.charFinish = curColumnStart;
							errorReporter.reportError(currentSpelling.toString()+
									": unterminated string", "", sourcePos);
							return Token.STRINGLITERAL;
						default:
							accept(); break;
					}
				}
				break;
				
			// ....
			case SourceFile.eof:	
				currentSpelling.append(Token.spell(Token.EOF));
				curColumnEnd = curColumnStart + 1;
				return Token.EOF;
			default:
				if (Character.isLetter(currentChar) || currentChar == '_') {
					accept();
					while(Character.isLetterOrDigit(currentChar) || currentChar == '_') {
						accept();
					}

					for (int i = 0; i < 11; i++) {
						if(Token.spell(i).equals(currentSpelling.toString())) {
							return i;
						}
					}

					if (currentSpelling.toString().equals("true") ||
						currentSpelling.toString().equals("false")) {
							return Token.BOOLEANLITERAL;
					}else{
						return Token.ID;
					}
				}else{
					break;
				}
		}
		
		accept(); 
		return Token.ERROR;
  	}

  	void skipSpaceAndComments() {
		// single space
		// tab space
		// new line
		// two cases of comments
		// end point: eof, meet real char
		boolean endSpaceAndComment = false;
		boolean isComment = false;
		// boolean isSingleComment = false;
		while (!endSpaceAndComment) {
			switch(currentChar) {
				case ' ': 
					// meet a single space, confirm belong to comment or not
					// if not from comment, try to eat them all
					while (currentChar == ' ') {
						currentChar = sourceFile.getNextChar();
						curColumnEnd += 1; // anyway we neet to increment end count
						if (!isComment) {
							curColumnStart = curColumnEnd;
						}
					}
					break;
				case '\n':
					// meet a new line, confirm belong to comment or not
					// if not from comment, try to eat them all and update line count
					while (currentChar == '\n') {
						currentChar = sourceFile.getNextChar();
						if (!isComment) {
							curLine += 1;
							finishLine = curLine;
							curColumnStart = 1;
							curColumnEnd = 1;
							// isSingleComment = false;
						}else{
							finishLine += 1;
							curColumnEnd = 1;
						}
					}
					break;
				case '\t':
					// meet a tab space, confirm belong to comment or not
					// if not from comment, try to eat the tab and update column count
					while (currentChar == '\t') {
						currentChar = sourceFile.getNextChar();
						curColumnEnd = curColumnEnd + 8 - curColumnEnd % 8 + 1;
						if (!isComment) {
							curColumnStart = curColumnEnd;
						}
					}
					break;
				case '/':
					if (isComment) {
						// if we meet the comment symbol inside the comment
						// we do not need to do anything except update end count
						currentChar = sourceFile.getNextChar();
						curColumnEnd += 1;
					}else{
						switch(inspectChar(1)) {
							case '*': // at this stage we confirm we start the mul-line comment
								isComment = true;
								currentChar = sourceFile.getNextChar();
								currentChar = sourceFile.getNextChar();
								curColumnEnd += 2;
								// handle general char inside the comment
								while (currentChar != SourceFile.eof && currentChar != '\n' && 
										currentChar != '\t' && currentChar != '*') {
									currentChar = sourceFile.getNextChar();
									curColumnEnd += 1;
								}
								break;
							case '/': // at this stage we confirm we start the single-line comment
								// isSingleComment = true;
								currentChar = sourceFile.getNextChar();
								currentChar = sourceFile.getNextChar();
								curColumnEnd += 2;
								while (currentChar != '\n' && currentChar != SourceFile.eof) {
									currentChar = sourceFile.getNextChar();
								}
								break;
							default: endSpaceAndComment = true; break;	
						}
					}
					break;
				// handle the end of multi-line comments
				case '*':
					if (!isComment) {
						endSpaceAndComment = true;
					}else{
						switch(inspectChar(1)) {
							case '/':
								currentChar = sourceFile.getNextChar();
								currentChar = sourceFile.getNextChar();
								curColumnEnd += 2;
								curLine = finishLine;
								isComment = false;
								curColumnStart = curColumnEnd;
								break;
							default: 
								currentChar = sourceFile.getNextChar();
								curColumnEnd += 1;
								break;
						}
					}
					break;
				case SourceFile.eof:
					if (isComment) {
						sourcePos.lineStart = curLine;
						sourcePos.charStart = curColumnStart;
						if (curColumnEnd == 1) {
							sourcePos.lineFinish = curLine;
							sourcePos.charFinish = curColumnStart;
						}else{
							sourcePos.lineFinish = finishLine;
							sourcePos.charFinish = curColumnEnd;
						}
						errorReporter.reportError(": unterminated comment", "", sourcePos);
					}
					curLine = finishLine;
					curColumnStart = curColumnEnd;
					endSpaceAndComment = true;
					break;
				default:
					if (isComment){
						currentChar = sourceFile.getNextChar();
						curColumnEnd += 1;
					}else{
						endSpaceAndComment = true;
					}
					break;
			}
		}
	}

  	public Token getToken() {
		Token tok;
		int kind;
		currentSpelling = new StringBuffer("");
		sourcePos = new SourcePosition();
		skipSpaceAndComments();

		// You must record the position of the current token somehow

		kind = nextToken();
		sourcePos.lineStart = curLine;
		sourcePos.lineFinish = finishLine;
		sourcePos.charStart = curColumnStart;
		sourcePos.charFinish = curColumnEnd - 1;

		tok = new Token(kind, currentSpelling.toString(), sourcePos);
		curColumnStart = curColumnEnd;

		// * do not remove these three lines
		if (debug)
			System.out.println(tok);
			return tok;
	}
}
