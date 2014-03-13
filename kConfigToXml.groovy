#!/usr/bin/env groovy

import groovy.xml.XmlUtil

def base = 'https://raw.github.com/torvalds/linux/master'

def Queue<String> input   = new LinkedList<>()
def Queue<String> output  = new LinkedList<>()

def archList = ['alpha','arc','arm','arm64','avr32','blackfin','c6x','cris','frv','hexagon','ia64','m32r','m68k','metag','microblaze','mips','mn10300','openrisc','parisc','powerpc','s390','score','sh','sparc','tile','unicore32','x86','xtensa']

archList.each {
    input << "arch/$it/Kconfig"
}

while (input.size() > 0)
{
    def currentFileName = input.pop()
    output << currentFileName

    def simpleFileName = currentFileName.tr('/', '_')
    def cacheFileName = 'test/' + simpleFileName + '.cache'
    def cacheFile = new File(cacheFileName)
    def xmlFileName = 'test/' + simpleFileName + '.xml'
    def xmlFile = new File(xmlFileName)

    if (!cacheFile.exists())
    {
        def sourceUrl = base + '/' + currentFileName
        cacheFile.text = sourceUrl.toURL().text
    }

    def Node root = LkcReader.read(cacheFile)
    xmlFile.text = XmlUtil.serialize(root)

    ((NodeList) root.breadthFirst()).getAt('source').each{ Node it ->
        def ref = it.attribute('reference')
        if (!output.contains(ref) && !input.contains(ref))
        {
            input << ref
        }
    }
}
