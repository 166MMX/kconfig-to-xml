import groovy.xml.XmlUtil
import com.initvoid.kconfig.io.LkcReader

def Queue<String> input   = new LinkedList<>()
def Queue<String> output  = new LinkedList<>()

def baseUrl   = 'https://raw.github.com/torvalds/linux/master'
def archList  = ['alpha', 'arc', 'arm', 'arm64', 'avr32', 'blackfin', 'c6x', 'cris', 'frv', 'hexagon', 'ia64', 'm32r', 'm68k', 'metag', 'microblaze', 'mips', 'mn10300', 'openrisc', 'parisc', 'powerpc', 's390', 'score', 'sh', 'sparc', 'tile', 'unicore32', 'x86', 'xtensa']

archList.each {
    input << "arch/$it/Kconfig"
}

while (input.size() > 0)
{
    def currentFileName  = input.pop()
    def simpleFileName   = currentFileName.tr('/', '_')
    def cacheFile        = new File("res/${simpleFileName}.cache")
    def xmlFile          = new File("res/${simpleFileName}.xml")

    output << currentFileName

    if (!cacheFile.exists())
    {
        def sourceUrl    = "$baseUrl/$currentFileName"
        cacheFile.text   = sourceUrl.toURL().text
    }

    def Node root        = LkcReader.read(cacheFile)
    xmlFile.text         = XmlUtil.serialize(root)

    ((NodeList) root.breadthFirst()).getAt('source').each{ Node it ->
        def ref = it.attribute('reference')
        if (!output.contains(ref) && !input.contains(ref))
        {
            input << ref
        }
    }
}
