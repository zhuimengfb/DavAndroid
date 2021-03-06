#!/bin/bash

declare -A android
android=([ca]=ca [cs]=cs [da]=da [de]=de [es]=es [fr]=fr [hu]=hu [it]=it [ja]=ja [nl]=nl [pl]=pl [pt]=pt [pt_BR]=pt-rBR [ru]=ru [sr]=sr [tr_TR]=tr-rTR [uk]=uk [zh_CN]=zh-rCN)

for lang in ${!android[@]}
do
	target_app=../app/src/main/res/values-${android[$lang]}
	target_cert4android=../cert4android/src/main/res/values-${android[$lang]}

	mkdir -p $target_app
	curl -n "https://www.transifex.com/api/2/project/davdroid/resource/app/translation/$lang?file" >$target_app/strings.xml

	mkdir -p $target_cert4android
	curl -n "https://www.transifex.com/api/2/project/davdroid/resource/cert4android/translation/$lang?file" >$target_cert4android/strings.xml
done
