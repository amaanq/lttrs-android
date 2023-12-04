for i in values-*; do
                if [ -d "${i}" ]; then
                        if [ -e "${i}/strings.xml" ]; then

                                locale="${i:7}"
                                echo $locale
                                echo -e "\t<locale android:name=\"${locale}\"/>" >> xml/locales_config.xml
                        fi
                fi
        done
