package com.initvoid.kconfig.expr.model

class ExpressionImpl implements Expression
{
    Type type
    Expression left
    Expression right

    static ExpressionImpl createSimpleExpression(Expression left)
    {
        def r = new ExpressionImpl()
        r.type = Type.NONE
        r.left = left
        r
    }

    static ExpressionImpl createOrExpression(Expression left, Expression right)
    {
        def r = new ExpressionImpl()
        r.type = Type.OR
        r.left = left
        r.right = right
        r
    }

    static ExpressionImpl createAndExpression(Expression left, Expression right)
    {
        def r = new ExpressionImpl()
        r.type = Type.AND
        r.left = left
        r.right = right
        r
    }

    static ExpressionImpl createNotExpression(Expression left)
    {
        def r = new ExpressionImpl()
        r.type = Type.NOT
        r.left = left
        r
    }

    static ExpressionImpl createEqualExpression(Expression left, Expression right)
    {
        def r = new ExpressionImpl()
        r.type = Type.EQUAL
        r.left = left
        r.right = right
        r
    }

    static ExpressionImpl createUnequalExpression(Expression left, Expression right)
    {
        def r = new ExpressionImpl()
        r.type = Type.UNEQUAL
        r.left = left
        r.right = right
        r
    }

    static ExpressionImpl createListExpression(Expression left)
    {
        def r = new ExpressionImpl()
        r.type = Type.LIST
        r.left = left
        r
    }

    static ExpressionImpl createSymbolExpression(Expression left)
    {
        def r = new ExpressionImpl()
        r.type = Type.SYMBOL
        r.left = left
        r
    }

    static ExpressionImpl createRangeExpression(Expression left, Expression right)
    {
        def r = new ExpressionImpl()
        r.type = Type.RANGE
        r.left = left
        r.right = right
        r
    }


}
