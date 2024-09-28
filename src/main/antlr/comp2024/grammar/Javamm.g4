grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
COL : ',';
LCURLY : '{' ;
RCURLY : '}' ;
RSQPAREN : '[';
LSQPAREN : ']';
LPAREN : '(' ;
RPAREN : ')' ;
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-';
DOT : '.';
GT : '>';
LT: '<';
AND : '&&';
NOT : '!';

IMPORT : 'import';
EXTENDS : 'extends';
CLASS : 'class' ;
INT : 'int' ;
VARARG: 'int' [ \t]* '...'; // FIXME: Ignore whitespaces when creating the actual type name in AST
STRING : 'String';
BOOL : 'boolean';
TRUE : 'true';
FALSE: 'false';
THIS: 'this';
PUBLIC : 'public' ;
RETURN : 'return' ;
NEW : 'new';
WHILE : 'while';
IF : 'if';
ELSE: 'else';
STATIC: 'static';
VOID: 'void';
LENGTH: 'lenght';

INTEGER : [0] | [1-9]+[0-9]* ;
ID : [a-zA-Z_$][a-zA-Z_$0-9]*;
WS : [ \t\n\r\f]+ -> skip ;
COMMENT : '/*' .*? '*/' -> skip ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;



program
    : (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=ID (DOT name+=ID)* SEMI
    ;

classDecl
    : CLASS name=ID (EXTENDS superClass=ID)?
        LCURLY
        varDecl* methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

//lacks 1 of them
type locals[boolean isArray=false, boolean isVarArg=false]
    : name=INT (RSQPAREN LSQPAREN{$isArray=true;})?
    | name=VARARG{$isVarArg=true;}
    | name=BOOL
    | name = ('String' | ID )
    | name = VOID
    ;

methodDecl locals[boolean isPublic=false, boolean isStatic=false]
    :  ('public'{$isPublic=true;})? ('static'{$isStatic=true;})? type name=ID '(' (param (',' param)* )? ')' '{'(varDecl)* (stmt)* retStmt '}' # Method
    | ('public'{$isPublic=true;})? ('static'{$isStatic=true;})? type name=ID '(' 'String' '[' ']' parameterName=ID ')' '{'(varDecl)* (stmt)* '}' # MainMethod
    ;

param
    : type name=ID
    ;

stmt
    : LCURLY stmt* RCURLY #CurlyStmt
    | ifStatment elseStatment #IfElseStmt
    | 'while' '(' expr ')' stmt #WhileStmt
    | expr ';' #ExpressionStmt
    | id=ID '=' expr ';' #AssignStmt
    | expr '=' expr ';' #ArrayAssignStmt
    //| id=ID '[' expr ']' '=' expr ';' #ArrayAssignStmt
    ;

ifStatment
    : 'if' '(' expr ')' stmt
    ;
elseStatment
    : 'else' stmt
    ;


expr
    : LPAREN expr RPAREN #ParenExpr
    | expr LSQPAREN expr RSQPAREN #ArrRefExpr
    | RSQPAREN (expr (COL expr)*)? LSQPAREN #ArrRefExpr
    | expr '[' expr ']' #ArrayIndex
    | expr DOT 'length' #LenCheckExpr
    | expr DOT name=ID LPAREN (expr (COL expr)*)? RPAREN #IdUseExpr
    | op=NOT expr #NotOp
    | expr (op='*' | op='/' ) expr  #BinaryExpr
    | expr (op='+' | op='-' ) expr #BinaryExpr
    | expr (op='<'| op='>' ) expr #BinaryExpr
    | expr op = '&&' expr #BoolOp
    | expr op =  '||' expr #BoolOp
    | value=INTEGER #IntegerLiteral
    | id = ID #Identifier
    | name=THIS #VarRefExpr
    | 'new' 'int' '[' expr ']' #NewIntArr
    | 'new' id = ID '(' ')'  #NewObject
    | value=TRUE #Bool
    | value=FALSE #Bool
    | THIS #ThisExpr
    | name=ID #IDExpr
    | INT #INTExpr
    ;


retStmt
    : RETURN expr ';' # ReturnStmt
    ;
