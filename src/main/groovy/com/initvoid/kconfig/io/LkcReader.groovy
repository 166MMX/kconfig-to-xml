package com.initvoid.kconfig.io

import com.initvoid.kconfig.expr.model.Expression
import com.initvoid.kconfig.expr.model.ExpressionImpl
import com.initvoid.kconfig.expr.model.Symbol
import com.initvoid.kconfig.expr.model.Type
import com.initvoid.kconfig.expr.parser.LkcExprLexer
import com.initvoid.kconfig.expr.parser.LkcExprParser
import groovy.transform.CompileStatic
import org.antlr.runtime.ANTLRStringStream
import org.antlr.runtime.CommonTokenStream
import org.apache.commons.lang3.StringEscapeUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.regex.Pattern

@CompileStatic
class LkcReader
{
    private static final Logger logger = LoggerFactory.getLogger(LkcReader.class)

//    static String T_EOL        = ''
//    static String T_HELP_TEXT  = ''
//    static String T_WORD_QUOTE  = ''
//    static String T_WORD  = ''
//    static String T_TYPE  = ''

    static String T_TRI_STATE            = 'tristate'
    static String T_STRING               = 'string'
    static String T_HEX                  = 'hex'
    static String T_INT                  = 'int'
    static String T_BOOLEAN              = 'boolean'
    static String T_BOOL                 = 'bool'

    static String T_DEFAULT              = 'default'
    static String T_DEFAULT_TRI_STATE    = 'def_tristate'
    static String T_DEFAULT_BOOLEAN      = 'def_bool'

    static String T_CONFIG               = 'config'
    static String T_MENU_CONFIG          = 'menuconfig'

    static String T_MAIN_MENU            = 'mainmenu'
    static String T_MENU                 = 'menu'
    static String T_END_MENU             = 'endmenu'
    static String T_CHOICE               = 'choice'
    static String T_END_CHOICE           = 'endchoice'
    static String T_IF                   = 'if'
    static String T_END_IF               = 'endif'

    static String T_HELP                 = 'help'
    static String T_HELP_BOLD            = '---help---'
    static String T_COMMENT              = 'comment'

    static String T_DEPENDS              = 'depends'
    static String T_ON                   = 'on'
    static String T_SELECT               = 'select'

    static String T_PROMPT               = 'prompt'
    static String T_OPTIONAL             = 'optional'
    static String T_RANGE                = 'range'
    static String T_VISIBLE              = 'visible'
    static String T_SOURCE               = 'source'
    static String T_OPTION               = 'option'

    static String T_AND                  = '&&'
    static String T_OR                   = '||'
    static String T_PARENTHESIS_LEFT     = '('
    static String T_PARENTHESIS_RIGHT    = ')'
    static String T_NOT                  = '!'
    static String T_EQUAL                = '='
    static String T_UNEQUAL              = '!='

    static String R_COMMENT              = '\\s*#.*$'
    static String R_SINGLE_QUOTE_STRING  = '\'(?:[^\\\\\']|\\\\.)*?\''
    static String R_DOUBLE_QUOTE_STRING  = '\"(?:[^\\\\\"]|\\\\.)*?\"'
    static String R_COMMAND_WORD         = '(?:[A-Za-z0-9_])+'
    static String R_PARAM_WORD           = '(?:[A-Za-z0-9_]|[-/.])+'
    static String R_SYMBOL               = "(?:$R_PARAM_WORD|$R_COMMAND_WORD|$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING)"
    static String R_EXPR_SIMPLE          = "(?:$R_SYMBOL\\s*$T_EQUAL\\s*$R_SYMBOL|$R_SYMBOL\\s*$T_UNEQUAL\\s*$R_SYMBOL|$R_SYMBOL)"
    static String R_EXPR_ENCLOSED        = "(?:\\(\\s*$R_EXPR_SIMPLE\\s*\\))"
    static String R_EXPR                 = "(?:(?:$T_NOT\\s*)?$R_EXPR_SIMPLE|(?:(?:$T_NOT\\s*)?$R_EXPR_ENCLOSED)+)"
    static String R_EXPRESSION_NESTED    = "(?:$R_EXPR(?:\\s*(?:\\|\\||&&)\\s*$R_EXPR)*)"

    static Pattern P_SIMPLE_COMMENT      = ~/^\s*#.*$|\s*#[^'"]*$/
    static Pattern P_COMMENT             = ~/$R_COMMENT/
    static Pattern P_WORD                = ~/$R_COMMAND_WORD|$R_PARAM_WORD/
    static Pattern P_WORD_QUOTE          = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING/
    static Pattern TOKENIZER             = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING|$R_EXPRESSION_NESTED/

    static def boolean isCommonParent (Node parent)
    {
        def boolean result = false
        def String name = parent.name()
        switch (name)
        {
            case T_MENU:
            case T_CHOICE:
            case T_IF:
                result = true
                break
            default:
                break
        }
        result
    }

    static def Node getParent (String command, Stack<Node> parents)
    {
        def Node parent = parents.peek()
        switch (command)
        {
            case T_COMMENT:
            case T_CONFIG:
            case T_MENU_CONFIG:
            case T_IF:
            case T_SOURCE:
            case T_MENU:
            case T_CHOICE:
                while (!isCommonParent(parent))
                {
                    parents.pop()
                    parent = parents.peek()
                }
                break
            case T_END_MENU:
                while (parent.name() != T_MENU)
                {
                    parent = parents.pop()
                }
                break
            case T_END_CHOICE:
                while (parent.name() != T_CHOICE)
                {
                    parent = parents.pop()
                }
                break
            case T_END_IF:
                while (parent.name() != T_IF)
                {
                    parent = parents.pop()
                }
                break

            default:
                break
        }
        parent
    }

    static def String getQuotedWord (String word)
    {
        // sym_expand_string_value
        String result = word
        if (result ==~ P_WORD_QUOTE)
        {
            result = result[1..-2]
            result = StringEscapeUtils.unescapeJava(result)
        }
        result
    }

    static def String getPrompt (List<String> tokens)
    {
        String result = null
        if (tokens.size() > 1)
        {
            result = getQuotedWord(tokens[1])
        }
        result
    }

    static def String getCondition (List<String> tokens)
    {
        String result = null
        int index = tokens.indexOf(T_IF)
        if (index != -1)
        {
            result = tokens[index + 1]
        }
        result
    }

    static def Expression parseExpr (String expression)
    {
        ANTLRStringStream inputStream = new ANTLRStringStream(expression)
        LkcExprLexer lexer = new LkcExprLexer(inputStream)
        if (lexer.numberOfSyntaxErrors > 0)
        {
            println "LkcExprLexer  SyntaxErrors $lexer.numberOfSyntaxErrors sourceExpressionString $expression"
        }
        CommonTokenStream tokens = new CommonTokenStream(lexer)
        LkcExprParser parser = new LkcExprParser(tokens)
        def expr = parser.expr()
        if (parser.numberOfSyntaxErrors > 0)
        {
            println "LkcExprParser SyntaxErrors $parser.numberOfSyntaxErrors sourceExpressionString $expression"
        }
        expr
    }

    static def Node convertExprModelToNodeTree (Expression expr)
    {
        Node r
        if (expr instanceof Symbol)
        {
            def symbolModel = expr as Symbol
            r = new Node(null, 'symbol', symbolModel.symbol)
        }
        else if (expr instanceof ExpressionImpl)
        {
            def exprModel = expr as ExpressionImpl
            switch (exprModel.type)
            {
                case Type.LIST:
                    r = new Node(null, 'expr')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    break
                case Type.NOT:
                    r = new Node(null, 'not')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    break
                case Type.EQUAL:
                    r = new Node(null, 'equal')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    r.append(convertExprModelToNodeTree(exprModel.right))
                    break
                case Type.UNEQUAL:
                    r = new Node(null, 'unequal')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    r.append(convertExprModelToNodeTree(exprModel.right))
                    break
                case Type.AND:
                    r = new Node(null, 'and')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    r.append(convertExprModelToNodeTree(exprModel.right))
                    break
                case Type.OR:
                    r = new Node(null, 'or')
                    r.append(convertExprModelToNodeTree(exprModel.left))
                    r.append(convertExprModelToNodeTree(exprModel.right))
                    break
            }
        }
        r
    }

    static def Node createExpr (String expression)
    {
        def model = parseExpr(expression)
        convertExprModelToNodeTree(model)
    }

    static def Node read (File kConfig)
    {
        def StringBuilder  multiLineBuffer  = new StringBuilder()
        def HelpReader     helpReader       = new HelpReader()
        def Stack<Node>    parents          = new Stack<>()
        def Node           root             = new Node(null, T_MENU)
        def Node           node
        def Node           parent
        def Map            attr
        def String         command
        def String         prompt
        def String         condition
        def String         helpText

        parents << root
        kConfig.text.eachLine { String line, int count ->
            if (helpReader.active)
            {
                helpText    = helpReader.read(line)
                if (helpText == null)
                {
                    return
                }
                node        = parents.pop()
                node.value  = helpText
            }

            line = line.replaceAll(P_SIMPLE_COMMENT, '')
            if (line.trim().empty)
            {
                return
            }
            if (line.endsWith('\\'))
            {
                multiLineBuffer << line
                multiLineBuffer << '\n'
                return
            }
            else if (multiLineBuffer.length() > 0)
            {
                multiLineBuffer << line
                line = multiLineBuffer.toString()
                line = line.replaceAll(~/\\\n/, ' ')
                multiLineBuffer.length = 0
            }

            List<String> tokens = (line =~ TOKENIZER).collect {it.toString()}

            command    = tokens[0]
            prompt     = getPrompt(tokens)
            condition  = getCondition(tokens)
            attr       = [:]
            parent     = getParent(command, parents)
            node
            switch (command)
            {
                case T_HELP:
                case T_HELP_BOLD:
                    helpReader.start()
                    node = new Node(parent, T_HELP)
                    parents << node
                    break

                case T_IF:
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    parents << node
                    break
                case T_END_IF:
                    break
                case T_CHOICE:
                    node = new Node(parent, command)
                    parents << node
                    break
                case T_END_CHOICE:
                    break
                case T_MENU:
                    if (prompt)
                        attr.prompt      = prompt
                    node = new Node(parent, command, attr)
                    parents << node
                    break
                case T_END_MENU:
                    break

                case T_CONFIG:
                    attr.symbol      = getQuotedWord(tokens[1])
                    node = new Node(parent, command, attr)
                    parents << node
                    break

                case T_MENU_CONFIG:
                    attr.symbol      = getQuotedWord(tokens[1])
                    node = new Node(parent, command, attr)
                    parents << node
                    break

                case T_COMMENT:
                    if (prompt)
                        attr.prompt      = prompt
                    node = new Node(parent, command, attr)
                    parents << node
                    break


                case T_BOOL:
                case T_BOOLEAN:
                case T_HEX:
                case T_INT:
                case T_STRING:
                case T_TRI_STATE:
                    if (prompt)
                        attr.prompt      = prompt
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_DEFAULT_BOOLEAN:
                case T_DEFAULT_TRI_STATE:
                case T_DEFAULT:
                    node = new Node(parent, command, attr)
                    new Node(node, 'expression', [string:tokens[1]]).append(createExpr(tokens[1]))
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_SELECT:
                    attr.symbol      = getQuotedWord(tokens[1])
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_VISIBLE:
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_OPTIONAL:
                    new Node(parent, command)
                    break

                case T_RANGE:
                    attr.from        = getQuotedWord(tokens[1])
                    attr.to          = getQuotedWord(tokens[2])
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_PROMPT:
                    if (prompt)
                        attr.text        = prompt
                    node = new Node(parent, command, attr)
                    if (condition)
                    {
                        new Node(node, 'condition', [string:condition]).append(createExpr(condition))
                    }
                    break

                case T_DEPENDS:
                    node = new Node(parent, command, attr)
                    new Node(node, 'expression', [string:tokens[2]]).append(createExpr(tokens[2]))
                    break

                case T_SOURCE:
                    attr.reference   = getQuotedWord(tokens[1])
                    new Node(parent, command, attr)
                    break

                case T_OPTION:
                    def parameter = null
                    if (tokens[1].contains('env='))
                    {
                        attr.env = getQuotedWord(tokens[1].split(/env=/)[1])
                    }
                    else if (tokens[1].matches(/defconfig_list|allnoconfig_y|modules/))
                    {
                        parameter = tokens[1]
                    }
                    def optionNode = new Node(parent, command, attr)
                    if (parameter)
                    {
                        new Node(optionNode, parameter)
                    }
                    break


                default:
                    println "$kConfig.name:$count: $line"
                    break
            }
            return
        }
        if (helpReader.active)
        {
            helpText    = helpReader.read('EOF')
            node        = parents.pop()
            node.value  = helpText
        }
        root
    }
}
