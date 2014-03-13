#!/usr/bin/env groovy

import groovy.xml.XmlUtil
import org.apache.commons.lang3.StringEscapeUtils

import java.util.regex.Pattern

@Grab(group='org.apache.commons', module='commons-lang3', version='3.2.1')

enum ExpressionType
{
    NONE,
    OR,
    AND,
    NOT,
    EQUAL,
    UNEQUAL,
    LIST,
    SYMBOL,
    RANGE
}

enum SymbolType
{
    UNKNOWN,
    BOOLEAN,
    TRI_STATE,
    INT,
    HEX,
    STRING,
    OTHER
}

enum PropertyType
{
    UNKNOWN,
    PROMPT,   /* prompt "foo prompt" or "BAZ Value" */
    COMMENT,  /* text associated with a comment */
    MENU,     /* prompt associated with a menuconfig option */
    DEFAULT,  /* default y */
    CHOICE,   /* choice value */
    SELECT,   /* select BAR */
    RANGE,    /* range 7..100 (for a symbol) */
    ENV,      /* value from environment variable */
    SYMBOL    /* where a symbol is defined */
}

class HelpReader
{
    private static  def int            INIT_INDENT_LENGTH  = -1
    private static  def Pattern        P_HELP_INDENT       = ~/^[ \t]+/

    private         def int            firstLength         = INIT_INDENT_LENGTH
    private         def StringBuilder  sb                  = null
    private         def Node           parent              = null
    private         def boolean        active              = false

    def void start (Node parent)
    {
        reset()
        active       = true
        this.parent  = parent

    }

    def void reset ()
    {
        firstLength  = INIT_INDENT_LENGTH
        sb           = new StringBuilder()
        active       = false
        parent       = null
    }

    def void finish ()
    {
        String helpText  = sb.toString().trim()
        new Node(parent, 'help', null, helpText)
        active           = false
        reset()
    }

    def boolean read (String line)
    {
        boolean emptyBuilder = sb.size() == 0

        if (!emptyBuilder && line.trim().empty)
        {
            sb << '\n'
            return false
        }

        String indent = getIndent (line)
        if (indent != null)
        {
            sb << indent
            sb << line.replaceAll(P_HELP_INDENT, '')
            sb << '\n'
            return false
        }

        finish()
        return true
    }

    private def String getIndent (String line)
    {
        int lineLength = line.length()
        int indentLength = 0

        for (int i = 0; i < lineLength; i++)
            if (line[i] == '\t')
                indentLength = (indentLength & ~7) + 8
            else if (line[i] == ' ')
                indentLength++
            else
                break

        String indent = null
        if (firstLength == INIT_INDENT_LENGTH)
        {
            firstLength = indentLength
            indent = ''
        }
        else if (indentLength >= firstLength)
        {
            indentLength -= firstLength
            indent = (' ' * indentLength)
        }
        indent
    }

    boolean getActive ()
    {
        return active
    }
}

class LkcReader
{
//    static String T_EOL        = ''
//    static String T_HELP_TEXT  = ''
//    static String T_WORD_QUOTE  = ''
//    static String T_WORD  = ''
//    static String T_TYPE  = ''

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
    static String T_ON           = 'on'
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
    static String R_EXPR_SIMPLE          = "(?:$R_SYMBOL\\s*$T_EQUAL\\s*$R_SYMBOL|$R_SYMBOL\\s*$T_UNEQUAL\\s*$R_SYMBOL|$R_SYMBOL)"
    static String R_EXPR_ENCLOSED        = "(?:\\(\\s*$R_EXPR_SIMPLE\\s*\\))"
    static String R_EXPR                 = "(?:(?:$T_NOT\\s*)?$R_EXPR_SIMPLE|(?:(?:$T_NOT\\s*)?$R_EXPR_ENCLOSED)+)"
    static String R_EXPRESSION_NESTED    = "(?:$R_EXPR(?:\\s*(?:\\|\\||&&)\\s*$R_EXPR)*)"
    static Pattern P_SIMPLE_COMMENT = ~/^\s*#.*$|\s*#[^'"]*$/
    static Pattern P_COMMENT     = ~/$R_COMMENT/
    static Pattern P_WORD        = ~/$R_COMMAND_WORD|$R_PARAM_WORD/
    static Pattern P_WORD_QUOTE  = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING/
    static Pattern TOKENIZER     = ~/$R_SINGLE_QUOTE_STRING|$R_DOUBLE_QUOTE_STRING|$R_EXPRESSION_NESTED/

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

    static def Node read (File kConfig)
    {
        Stack<Node> parents = new Stack<>()
        def Node root = new Node(null, T_MENU)
        parents << root

        HelpReader helpReader = new HelpReader()

        kConfig.text.eachLine { String line, int count ->
            if (helpReader.active)
            {
                helpReader.read(line)
                if (helpReader.active)
                {
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

            def Map   attr    = [:]
            def Node  parent  = getParent(command, parents)
            def Node  node
            def String prompt     = getPrompt(tokens)
            def String condition  = getCondition(tokens)
            switch (command)
            {
                case T_HELP:
                case T_HELP_BOLD:
                    helpReader.start(parent)
                    break

                case T_BOOL:
                case T_BOOLEAN:
                case T_HEX:
                case T_INT:
                case T_STRING:
                case T_TRI_STATE:
                    if (prompt)
                        attr.prompt      = prompt
                    if (condition)
                        attr.condition   = condition
                    new Node(parent, command, attr)
                    break

                case T_DEFAULT_BOOLEAN:
                case T_DEFAULT_TRI_STATE:
                case T_DEFAULT:
                    attr.expression  = tokens[1]
                    if (condition)
                        attr.condition   = condition
                    new Node(parent, command, attr)
                    break

                case T_SELECT:
                    attr.symbol      = tokens[1]
                    if (condition)
                        attr.condition   = condition
                    new Node(parent, command, attr)
                    break

                case T_RANGE:
                    attr.from        = tokens[1]
                    attr.to          = tokens[2]
                    if (condition)
                        attr.condition   = condition
                    new Node(parent, command, attr)
                    break

                case T_PROMPT:
                    attr.text        = getPrompt(tokens)
                    if (condition)
                        attr.condition   = condition
                    new Node(parent, command, attr)
                    break

                case T_DEPENDS:
                    attr.expression  = tokens[2]
                    new Node(parent, command, attr)
                    break

                case T_SOURCE:
                    attr.reference   = getQuotedWord(tokens[1])
                    new Node(parent, command, attr)
                    break

                case T_IF:
                    if (condition)
                        attr.condition   = condition
                    node = new Node(parent, command, attr)
                    parents << node
                    break
                case T_END_IF:
                    break
                case T_CHOICE:
                    node = new Node(parent, command, null)
                    parents << node
                    break
                case T_END_CHOICE:
                    break
                case T_MENU:
                    attr.prompt      = getPrompt(tokens)
                    node = new Node(parent, command, attr)
                    parents << node
                    break
                case T_END_MENU:
                    break

                case T_CONFIG:
                    attr.symbol      = tokens[1]
                    node = new Node(parent, command, attr)
                    parents << node
                    break

                case T_MENU_CONFIG:
                    attr.symbol      = tokens[1]
                    node = new Node(parent, command, attr)
                    parents << node
                    break

                case T_COMMENT:
                    attr.prompt      = getPrompt(tokens)
                    node = new Node(parent, command, attr)
                    parents << node
                    break

                default:
                    println "$count: $line"
                    break
            }
            return
        }
        root
    }
}

def cacheFile = new File('arch_x86_KConfig.cache')
if (!cacheFile.exists())
{
    def sourceUrl = 'https://raw.github.com/torvalds/linux/master/arch/x86/Kconfig'
    cacheFile.text = sourceUrl.toURL().text
}
def Node root = LkcReader.read(cacheFile)
println XmlUtil.serialize(root)
