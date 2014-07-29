grammar LkcExprLang;

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

// T_LPAREN > T_NOT > T_EQUAL, T_UNEQUAL > T_AND > T_OR
// T_OR < T_AND < T_EQUAL, T_UNEQUAL < T_NOT < T_LPAREN

expr
    :               left=or_expr
        EOF
    ;

or_expr
    :               left=and_expr
    (   T_OR        right=or_expr
    )?
    ;

and_expr
    :               left=comp_expr
    (   T_AND       right=and_expr
    )?
    ;

comp_expr
    :               left=not_expr
    (   T_EQUAL     right=not_expr
    |   T_UNEQUAL   right=not_expr
    )?
    ;

not_expr
    :               left=list_expr
    |   T_NOT       right=not_expr
    ;

list_expr
    :               left=symbol
    |   T_LPAREN    right=or_expr   T_RPAREN
    ;

symbol
    :	CONST
    |	STRING
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
