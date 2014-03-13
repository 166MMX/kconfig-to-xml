#!/usr/bin/env groovy

import groovy.xml.XmlUtil

def cacheFile = new File('arch_x86_KConfig.cache')
if (!cacheFile.exists())
{
    def sourceUrl = 'https://raw.github.com/torvalds/linux/master/arch/x86/Kconfig'
    cacheFile.text = sourceUrl.toURL().text
}
def Node root = LkcReader.read(cacheFile)
println XmlUtil.serialize(root)
