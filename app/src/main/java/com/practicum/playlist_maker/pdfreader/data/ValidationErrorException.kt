package com.practicum.playlist_maker.pdfreader.data

class ValidationErrorException : Exception{
    var errorCode: Int = -1
    constructor(errorCode: Int, ) : super(){
        this.errorCode = errorCode
    }
    constructor(errorCode: Int, message: String?) : super(message){
        this.errorCode = errorCode
    }
}