# QR-RaptorQ码文件传输系统技术文档

## 项目概述
QR-RaptorQ文件传输系统是一个基于RaptorQ码的文件传输解决方案，通过QR码作为传输媒介，实现可靠的文件传输。系统具有抗丢包、自动纠错、无序传输等特性，特别适合在不稳定的传输环境下使用。



### 数据包格式
```json
{
    "header": {
        "magic": "FLQR",
        "fileSize": <文件大小>,
        "fileName": <文件名>,
        "totalBlocks": <总块数>,
        "blockIndex": <块索引>,
        "checksum": <校验和>
    },
    "encoding": {
        "degree": <编码度>,
        "sourceBlocks": [<源块索引列表>],
        "checksum": <编码校验和>
    },
    "payload": <Base64编码的数据>
}
```

### 数据包示例
```json
{"header":{"magic":"FLQR","fileSize":22881,"fileName":"1741163994917.png","blockIndex":719,"totalBlocks":45,"checksum":341,"reserved":0},"encoding":{"seed":719,"degree":6,"sourceBlocks":[5,10,11,12,22,25],"checksum":25411},"payload":"\/j8e9ulhBVpi+SJ6sxq\/4Ma92xOGqOTH2tIErvtl+kkTVCsiZuFtVB7UHITgl2BrfUTolZcF2+nk3rRE7+Pz3T3+hCiDtPGzbbQ2brlmLw\/z9ux+Bg576YITN1jibWhoBfvXoZl+RWonJf1U+uep6G4DOo3IiYpeY9TdPHMSE8Hy+kmAENFbw+5oeNhnZa4pzWr4ktM5hnWsLY8noO8Y6vJ9i417G1Z46mpGZLsQoVI0pSPy0RDfrC8QFzy8ksTUEnO\/U60QQ5V5KgnSI7rEH76nf+ZixDBijcXERqBmRpOChxcnnpSHLA0ncAdXmXWsckp+w571ds6jtK9MekAEFoVfmOTROPCuqoDLk2KWk2+8q7+MGtBG+tne9G81jgMW7qG6iDBXBsemiWgBSQ+dfREAwCXci42BXYi4YQrfGrD2FXQeW+efPH3Kll074oJ5a2udFODbk965rEO5aYeb9WapcsZt8RLGap9xF9B7fRFAdH4j7l+Z4Y+chccfNEwnJvzQE+aTNFFf2ghhFNNVdqgwp80937IVDcuFKmzow6KFj\/unbHGKHf4NaA7UztPpv1X2C1kgnzCG+HmdsRBqPPmUanExy8MoerTOwwnya2qsbZ3+IrgKS3hbIPldQghX55wl2Wecbhzn\/nXv3TVtRswkX48O3vn9RKE0umTGfbs="}
```
