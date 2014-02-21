#!/usr/bin/env groovy
import org.apache.commons.lang3.StringEscapeUtils

import java.util.regex.Pattern

@Grab(group='org.apache.commons', module='commons-lang3', version='3.2.1')

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

    static String T_DEPENDS_ON   = 'depends on'
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

    static Pattern T_WORD        = ~/(?:[A-Za-z0-9_]|[-\/.])+/
    static Pattern T_WORD_QUOTE  = ~/'(?:[^\\']|\\.)*'|"(?:[^\\"]|\\.)*"/
    static Pattern TOKENIZER     = ~/'(?:[^\\']|\\.)*'|"(?:[^\\"]|\\.)*"|\s+#.*|[^\s]+/


    static def read (File kConfig)
    {
        int ifCounter = 0
        kConfig.text.eachLine { String line, int count ->
            if (line.trim().length() == 0)
            {
                return
            }
            def sb = new StringBuilder()
            def tokens = line =~ TOKENIZER
            switch (tokens[0]) {
                case T_BOOL:
                case T_BOOLEAN:
                case T_HEX:
                case T_INT:
                case T_STRING:
                case T_TRI_STATE:
                    def type = tokens[0]
                    if (type == T_BOOLEAN)
                    {
                        type = T_BOOL
                    }
                    sb << '<type value="'
                    sb << StringEscapeUtils.escapeXml(type.toString())
                    sb << '">'
                    if (tokens.size() > 2)
                    {
                        sb << '<prompt if="'
                        sb << '">'
                        sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                        sb << '</prompt>'
                    }
                    sb << '</type>'
                    break

                case T_DEFAULT_BOOLEAN:
                    sb << '<type value="'
                    sb << T_BOOL
                    sb << '">'
                    sb << '</type>'
                    sb << '<default if="'
                    sb << '">'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</default>'
                    break
                case T_DEFAULT_TRI_STATE:
                    sb << '<type value="'
                    sb << T_TRI_STATE
                    sb << '">'
                    sb << '</type>'
                    sb << '<default if="'
                    sb << '">'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</default>'
                    break
                case T_DEFAULT:
                    sb << '<default if="'
                    sb << '">'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</default>'
                    break

                case T_IF:
                    if (ifCounter > 0)
                    {
                        ifCounter--
                        sb << '</if>'
                    }
                    ifCounter++
                    sb << '<if expr="'
                    sb << '">'
                    break
                case T_END_IF:
                    ifCounter--
                    sb << '</if>'
                    break
                case T_CHOICE:
                    sb << '<choice>'
                    break
                case T_END_CHOICE:
                    sb << '</choice>'
                    break
                case T_MENU:
                    sb << '<menu>'
                    break
                case T_END_MENU:
                    sb << '</menu>'
                    break

                case T_CONFIG:
                case T_MENU_CONFIG:

                case T_COMMENT:
                case T_HELP:
                case T_HELP_BOLD:

                case T_DEPENDS_ON:
                    break
                case T_SELECT:
                    sb << '<select if="'
                    sb << '">'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</select>'
                    break

                case T_RANGE:
                    sb << '<range from="'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '" to="'
                    sb << StringEscapeUtils.escapeXml(tokens[2].toString())
                    sb << '" if="'
                    sb << '">'
                    sb << '</range>'
                    break

                case T_PROMPT:
                    sb << '<prompt if="'
                    sb << '">'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</prompt>'
                    break

                case T_SOURCE:
                    sb << '<source>'
                    sb << StringEscapeUtils.escapeXml(tokens[1].toString())
                    sb << '</source>'
                    break
            }
            println sb
        }
    }
}

def cacheFile = new File('arch_x86_KConfig.cache')
if (!cacheFile.exists())
{
    def sourceUrl = 'https://raw.github.com/torvalds/linux/master/arch/x86/Kconfig'
    cacheFile.text = sourceUrl.toURL().text
}
KConfigToXml.read(cacheFile)
