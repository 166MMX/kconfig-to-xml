package name.harth.conv.model

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
