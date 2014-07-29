grammar LkcExpr;

options {
    language=Java;
}

tokens {
    T_OR       = '||'  ;
    T_AND      = '&&'  ;
    T_EQUAL    = '='   ;
    T_UNEQUAL  = '!='  ;
    T_NOT      = '!'   ;
    T_LPAREN   = '('   ;
    T_RPAREN   = ')'   ;
    T_DQUOT    = '"'   ;
    T_SQUOT    = '\''  ;
}

@header {
package com.initvoid.kconfig.expr.parser;

import com.initvoid.kconfig.expr.model.Expression;
import com.initvoid.kconfig.expr.model.ExpressionImpl;
import com.initvoid.kconfig.expr.model.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
}

@lexer::header {
package com.initvoid.kconfig.expr.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
}

@members {
private static final Logger logger = LoggerFactory.getLogger(LkcExprParser.class);

@Override public void displayRecognitionError(String[] tokenNames, RecognitionException ex)
{
    String hdr = getErrorHeader(ex);
    String msg = getErrorMessage(ex, tokenNames);
    if (logger.isErrorEnabled()) logger.error(hdr + " " + msg, ex);
}
}

@lexer::members {
private static final Logger logger = LoggerFactory.getLogger(LkcExprLexer.class);

@Override public void displayRecognitionError(String[] tokenNames, RecognitionException ex)
{
    String hdr = getErrorHeader(ex);
    String msg = getErrorMessage(ex, tokenNames);
    if (logger.isErrorEnabled()) logger.error(hdr + " " + msg, ex);
}
}

// T_LPAREN > T_NOT > T_EQUAL, T_UNEQUAL > T_AND > T_OR
// T_OR < T_AND < T_EQUAL, T_UNEQUAL < T_NOT < T_LPAREN

expr
    returns                                     [Expression result]
    :               left=or_expr                { result = $left.result; }
        EOF
    ;

or_expr
    returns                                     [Expression result]
    :               left=and_expr               { result = $left.result; }
    (   T_OR        right=or_expr               { result = ExpressionImpl.createOrExpression(       $result,      $right.result); }
    )?
    ;

and_expr
    returns                                     [Expression result]
    :               left=comp_expr              { result = $left.result; }
    (   T_AND       right=and_expr              { result = ExpressionImpl.createAndExpression(      $result,      $right.result); }
    )?
    ;

comp_expr
    returns                                     [Expression result]
    :               left=not_expr               { result = $left.result; }
    (   T_EQUAL     right=not_expr              { result = ExpressionImpl.createEqualExpression(    $left.result, $right.result); }
    |   T_UNEQUAL   right=not_expr              { result = ExpressionImpl.createUnequalExpression(  $left.result, $right.result); }
    )?
    ;

not_expr
    returns                                     [Expression result]
    :               left=list_expr              { result = $left.result; }
    |   T_NOT       right=not_expr              { result = ExpressionImpl.createNotExpression(      $right.result); }
    ;

list_expr
    returns                                     [Expression result]
    :               left=symbol                 { result = $left.result; }
    |   T_LPAREN    right=or_expr   T_RPAREN    { result = ExpressionImpl.createListExpression(     $right.result); }
    ;

symbol
    returns                                     [Symbol result]
    :	CONST                                   { result = new Symbol($CONST.text); }
    |	STRING                                  { result = new Symbol($STRING.text); }
    ;

DASHES
    :   '---'                                   {$channel=HIDDEN;}
    ;

COMMENT
    :   '#' ~('\r'|'\n')* EOL                   {$channel=HIDDEN;}
    ;

WS  :   (' '|'\t')                              {$channel=HIDDEN;}
    ;

EOL :   '\r'? '\n'                              {$channel=HIDDEN;}
    ;

CONST
    :   ('a'..'z'|'A'..'Z'|'0'..'9'|'_'|'-'|'/'|'.')+
    ;

STRING
    :    '"' ( ESC_SEQ | ~('\\'| '"') )*  '"'
    |   '\'' ( ESC_SEQ | ~('\\'|'\'') )* '\''
    ;

fragment HEX_DIGIT
    :   ('0'..'9'|'a'..'f'|'A'..'F')
    ;

fragment ESC_SEQ
    :   '\\' ('b'|'t'|'n'|'f'|'r'|'\"'|'\''|'\\')
    |   UNICODE_ESC
    |   HEX_ESC
    |   OCTAL_ESC
    ;

fragment OCTAL_ESC
    :   '\\' ('0'..'3') ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7') ('0'..'7')
    |   '\\' ('0'..'7')
    ;

fragment HEX_ESC
    :   '\\' 'x' HEX_DIGIT HEX_DIGIT
    ;

fragment UNICODE_ESC
    :   '\\' 'u' HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT
    ;
