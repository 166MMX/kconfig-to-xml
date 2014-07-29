package com.initvoid.kconfig.io

import groovy.transform.CompileStatic

import java.util.regex.Pattern

@CompileStatic
class HelpReader
{
    private static  def Pattern        TRIM_PATTERN  = ~/^[ \t]+|\s+$/
    private static  def int            INIT_LENGTH   = -1

    private         def boolean        active        = false
    private         def int            baseIndent    = INIT_LENGTH
    private         def StringBuilder  sb            = new StringBuilder()

    def boolean getActive ()
    {
        return active
    }

    def void start ()
    {
        reset()
        active       = true
    }

    def void reset ()
    {
        active       = false
        baseIndent   = INIT_LENGTH
        sb.length    = 0
    }

    def String read (String line)
    {
        if (line.trim().empty)
        {
            if (sb.length() != 0)
            {
                sb << '\n'
            }
            return null
        }

        def String indent = getIndent(line)
        if (indent != null)
        {
            sb << indent
            sb << line.replaceAll(TRIM_PATTERN, '')
            sb << '\n'
            return null
        }

        def String text = finish()
        return text
    }

    private def String finish ()
    {
        def String text = sb.toString().normalize().expand()

        for (def int i = text.length() - 1; i >= 0; i--)
        {
            if (text[i] == '\n')
                continue
            text = text[0..i]
            break
        }

        reset()

        return text
    }

    private def String getIndent (String line)
    {
        int l = line.length()
        int indentLength = 0

        for (int i = 0; i < l; i++)
            if (line[i] == '\t')
                indentLength = (indentLength & ~7) + 8
            else if (line[i] == ' ')
                indentLength++
            else
                break

        String indent = null
        if (baseIndent == INIT_LENGTH)
        {
            baseIndent = indentLength
            indent = ''
        }
        else if (indentLength >= baseIndent)
        {
            indentLength -= baseIndent
            indent = (' ' * indentLength)
        }
        return indent
    }
}
