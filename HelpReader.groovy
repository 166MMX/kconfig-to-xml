import java.util.regex.Pattern

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
