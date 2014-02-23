#!/usr/bin/env groovy

import groovy.xml.XmlUtil
import org.apache.commons.lang3.StringEscapeUtils

import java.util.regex.Matcher
import java.util.regex.Pattern

@Grab(group='org.apache.commons', module='commons-lang3', version='3.2.1')

enum ParserState {
    INITIAL,
    COMMAND,
    PARAM,
    STRING,
    HELP,
    EOL
}

class KConfigToXml
{
    static String T_TRI_STATE  = 'tristate'
    static String T_STRING     = 'string'
    static String T_HEX        = 'hex'
    static String T_INT        = 'int'
    static String T_BOOLEAN    = 'boolean'
    static String T_BOOL       = 'bool'

    static String T_DEFAULT            = 'default'
    static String T_DEFAULT_TRI_STATE  = 'def_tristate'
    static String T_DEFAULT_BOOLEAN    = 'def_bool'

    static String T_CONFIG       = 'config'
    static String T_MENU_CONFIG  = 'menuconfig'

    static String T_MAIN_MENU    = 'mainmenu'
    static String T_MENU         = 'menu'
    static String T_END_MENU     = 'endmenu'
    static String T_CHOICE       = 'choice'
    static String T_END_CHOICE   = 'endchoice'
    static String T_IF           = 'if'
    static String T_END_IF       = 'endif'

    static String T_HELP         = 'help'
    static String T_HELP_BOLD    = '---help---'
    static String T_COMMENT      = 'comment'

    static String T_DEPENDS      = 'depends'
    static String T_SELECT       = 'select'

    static String T_PROMPT       = 'prompt'
    static String T_OPTIONAL     = 'optional'
    static String T_RANGE        = 'range'
    static String T_VISIBLE      = 'visible'
    static String T_SOURCE       = 'source'
    static String T_OPTION       = 'option'

    static String T_AND                = '&&'
    static String T_OR                 = '||'
    static String T_PARENTHESIS_LEFT   = '('
    static String T_PARENTHESIS_RIGHT  = ')'
    static String T_NOT                = '!'
    static String T_EQUAL              = '='
    static String T_UNEQUAL            = '!='

    static String R_COMMENT              = '\\s*#.*$'
    static String R_SINGLE_QUOTE_STRING  = '\'(?:[^\\\\\']|\\\\.)*?\''
    static String R_DOUBLE_QUOTE_STRING  = '\"(?:[^\\\\\"]|\\\\.)*?\"'
    static String R_COMMAND_WORD         = '(?:[A-Za-z0-9_])+'
    static String R_PARAM_WORD           = '(?:[A-Za-z0-9_]|[-/.])+'
    static String R_SYMBOL               = "(?:$R_COMMAND_WORD|$R_PARAM_WORD|$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING)"
    static String R_EXPRESSION           = "(?:$R_SYMBOL\\s*$T_EQUAL\\s*$R_SYMBOL|$R_SYMBOL\\s*$T_UNEQUAL\\s*$R_SYMBOL|$R_SYMBOL)"
    static String R_EXPRESSION_NESTED    = "(?:\\(\\s*$R_EXPRESSION\\s*\\)|$T_NOT\\s*$R_EXPRESSION|$R_EXPRESSION\\s*\\|\\|\\s*$R_EXPRESSION|$R_EXPRESSION\\s*&&\\s*$R_EXPRESSION|$R_EXPRESSION)+"
    static Pattern P_SIMPLE_COMMENT = ~/^\s*#.*$|\s*#[^'"]*$/
    static Pattern P_HELP_INDENT = ~/^[ \t]+/
    static Pattern P_COMMENT     = ~/$R_COMMENT/
    static Pattern P_WORD        = ~/$R_COMMAND_WORD|$R_PARAM_WORD/
    static Pattern P_WORD_QUOTE  = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING/
    static Pattern TOKENIZER     = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING|$R_EXPRESSION_NESTED/

    static String getHelpIndent (String s, int firstLength)
    {
        if (firstLength == -1)
        {
            firstLength = 0
        }
        String indent = null
        def sLength = s.length()
        int indentLength = 0

        for (int i = 0; i < sLength; i++)
            if (s[i] == '\t')
                indentLength = (indentLength & ~7) + 8
            else if (s[i] == ' ')
                indentLength++
            else
                break

        if (indentLength >= firstLength)
        {
            indentLength -= firstLength
            indent = (' ' * indentLength)
        }
        indent
    }

    static def boolean isSimpleContainerNode (Node parent)
    {
        switch (parent.name())
        {
            case T_HELP:
            case T_CONFIG:
            case T_COMMENT:
                true
                break
            default:
                false
                break
        }
    }

    static def Node getParent (String command, Stack<Node> parents)
    {
        Node parent = parents.peek()
        switch (command)
        {
            case T_BOOL:
            case T_BOOLEAN:
            case T_HEX:
            case T_INT:
            case T_STRING:
            case T_TRI_STATE:
                break

            case T_DEFAULT_BOOLEAN:
            case T_DEFAULT_TRI_STATE:
            case T_DEFAULT:
                break

            case T_COMMENT:
            case T_CONFIG:
            case T_MENU_CONFIG:
            case T_MENU:
            case T_IF:
            case T_SOURCE:
            case T_CHOICE:
                while (isSimpleContainerNode(parent))
                {
                    parents.pop()
                    parent = parents.peek()
                }
                break
            case T_END_MENU:
                while (!(parent.name() == T_MENU || parent.name() == T_MENU_CONFIG))
                    parent = parents.pop()
                break
            case T_END_CHOICE:
                while (!(parent.name() == T_CHOICE))
                    parent = parents.pop()
                break
            case T_END_IF:
                while (!(parent.name() == T_IF))
                    parent = parents.pop()
                break

            case T_HELP:
            case T_HELP_BOLD:
                break

            case T_DEPENDS:
            case T_SELECT:
            case T_RANGE:
            case T_PROMPT:
                break

            default:
                break
        }
        parent
    }

    static def Map readIfExpr (List<String> tokens, Map attributes)
    {
        int indexOfIf = tokens.indexOf(T_IF)
        if (indexOfIf != -1)
        {
            if (attributes == null)
            {
                attributes = [:]
            }
            String expr = tokens[indexOfIf + 1]
            attributes['if'] = expr
        }
        attributes
    }

    static def Node readPrompt (List<String> tokens, Node parent)
    {
        String prompt = null
        if (tokens.size() > 1)
        {
            prompt = tokens[1]
        }
        if (prompt ==~ P_WORD_QUOTE)
        {
            prompt = prompt[1..-2]
            prompt = StringEscapeUtils.unescapeJava(prompt)
        }

        Map attributes = null
        attributes = readIfExpr(tokens, attributes)

        new Node(parent, T_PROMPT, attributes, prompt)
    }

    static def void read (File kConfig)
    {
        Node root = new Node(null, 'kconfig')
        Stack<Node> parents = new Stack<>()
        parents << root

        ParserState   state             = ParserState.INITIAL
        int           firstIndentLength = -1
        StringBuilder helpStringBuilder = null

        kConfig.text.eachLine { String line, int count ->
            if (state == ParserState.HELP)
            {
                if (line.trim().empty)
                {
                    helpStringBuilder << '\n'
                    return
                }
                String indent = getHelpIndent(line, firstIndentLength)
                if (indent == null)
                {
                    Node parent = parents.pop()
                    String helpString = helpStringBuilder
                    parent.setValue(helpString.trim())

                    state = ParserState.INITIAL
                    helpStringBuilder = null
                    firstIndentLength = -1
                }
                else
                {
                    if (firstIndentLength == -1)
                    {
                        firstIndentLength = indent.length()
                        indent = ''
                    }
                    helpStringBuilder << indent
                    helpStringBuilder << line.replaceAll(P_HELP_INDENT, '')
                    helpStringBuilder << '\n'
                    return
                }
            }
            line = line.replaceAll(P_SIMPLE_COMMENT, '')
            if (line.trim().empty)
            {
                return
            }
            List<String> tokens = (line =~ TOKENIZER).collect {it.toString()}
            String command = tokens[0]
            Node parent = getParent(command, parents)
            switch (command) {
                case T_BOOL:
                case T_BOOLEAN:
                case T_HEX:
                case T_INT:
                case T_STRING:
                case T_TRI_STATE:
                    String typeValue = tokens[0]
                    if (typeValue == T_BOOLEAN)
                    {
                        typeValue = T_BOOL
                    }

                    Map typeAttributes = [
                            value: typeValue
                    ]

                    Node typeNode = new Node(parent, 'type', typeAttributes)
                    readPrompt(tokens, typeNode)
                    break

                case T_DEFAULT_BOOLEAN:
                    String typeValue = T_BOOL
                    String defaultExpr = tokens[1]

                    new Node(parent, 'type', [value: typeValue])
                    new Node(parent, T_DEFAULT, null, defaultExpr)
                    break
                case T_DEFAULT_TRI_STATE:
                    String typeValue = T_TRI_STATE
                    String defaultExpr = tokens[1]

                    new Node(parent, 'type', [value: typeValue])
                    new Node(parent, T_DEFAULT, null, defaultExpr)
                    break
                case T_DEFAULT:
                    Map attributes = null
                    String expr = tokens[1]

                    attributes = readIfExpr(tokens, attributes)
                    new Node(parent, T_DEFAULT, attributes, expr)
                    break

                case T_IF:
                    Map attributes = null

                    attributes = readIfExpr(tokens, attributes)
                    Node ifNode = new Node(parent, T_IF, attributes)
                    parents << ifNode
                    break
                case T_END_IF:
                    break
                case T_CHOICE:
                    Node menuNode = new Node(parent, T_CHOICE)
                    parents << menuNode
                    break
                case T_END_CHOICE:
                    break
                case T_MENU:
                    Node menuNode = new Node(parent, T_MENU)
                    readPrompt(tokens, menuNode)
                    parents << menuNode
                    break
                case T_END_MENU:
                    break

                case T_CONFIG:
                    String symbol = tokens[1]
                    Node configNode = new Node(parent, T_CONFIG, [symbol:symbol])
                    parents << configNode
                    break

                case T_MENU_CONFIG:
                    String symbol = tokens[1]
                    Node configNode = new Node(parent, 'menu_config', [symbol:symbol])
                    parents << configNode
                    break

                case T_COMMENT:
                    Node commentNode = new Node(parent, T_COMMENT)
                    readPrompt(tokens, commentNode)
                    parents << commentNode
                    break

                case T_HELP:
                case T_HELP_BOLD:
                    Node helpNode = new Node(parent, T_HELP)
                    parents << helpNode
                    state = ParserState.HELP
                    helpStringBuilder = new StringBuilder()
                    firstIndentLength = -1
                    break

                case T_DEPENDS:
                    String expr = tokens[2]
                    new Node(parent, 'depends_on', null, expr)
                    break

                case T_SELECT:
                    Map attributes = null
                    String symbol = tokens[1]

                    attributes = readIfExpr(tokens, attributes)
                    new Node(parent, T_SELECT, attributes, symbol)
                    break

                case T_RANGE:
                    Map attributes = [:]
                    attributes['from'] = tokens[1]
                    attributes['to'] = tokens[2]

                    attributes = readIfExpr(tokens, attributes)
                    new Node(parent, T_RANGE, attributes)
                    break

                case T_PROMPT:
                    readPrompt(tokens, parent)
                    break

                case T_SOURCE:
                    String path = tokens[1]
                    if (path ==~ P_WORD_QUOTE)
                    {
                        path = path[1..-2]
                        path = StringEscapeUtils.unescapeJava(path)
                    }

                    new Node(parent, T_SOURCE, null, path)
                    break

                default:
                    break
            }
            return
        }
        println XmlUtil.serialize(root)
    }
}

def cacheFile = new File('arch_x86_KConfig.cache')
if (!cacheFile.exists())
{
    def sourceUrl = 'https://raw.github.com/torvalds/linux/master/arch/x86/Kconfig'
    cacheFile.text = sourceUrl.toURL().text
}
KConfigToXml.read(cacheFile)
