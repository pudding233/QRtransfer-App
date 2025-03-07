# QR喷泉码文件传输系统技术文档

## 项目概述
QR喷泉码文件传输系统是一个基于喷泉码（Fountain Code）的文件传输解决方案，通过QR码作为传输媒介，实现可靠的文件传输。系统具有抗丢包、自动纠错、无序传输等特性，特别适合在不稳定的传输环境下使用。

## 初步开发指南
全程使用兼容性好的方案,如果有现成可以使用的库,请告知我,我会下载下来供你使用。
包名统一为com.pudding233.qrtransfer
整体界面:
app使用现代风格,主色调蓝色,展示两个进入入口,发送文件和接收文件。
接收界面:
接收文件页面使用摄像头实时画面覆盖全屏,下方有以气泡的形式显示实时信息,当前传输速度(使用存储单位),已接收块的数量等四个信息,自行拟定。
接收数据包的速度需要迅速(以每秒10张为标准),解码进度应该在调试日志中有所体现,以方便调试。
发送界面:
发送页面也需要完成,喷泉码发送,图片每秒20张更替。
数据加解码:
接收的数据包中的传输喷泉码,具体数据包结构以下文的数据包格式和数据包示例为主。文件接收完毕之后保存在安卓公共下载地址，并以弹窗的形式告知用户已传输完成。传输完成之后结束二维码识别。
调试日志中输出的信息使用中文,日志中必须有当前扫描到的二维码信息、解码进度。
请根据这些信息,完成数据传输主要内容的开发工作。

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
