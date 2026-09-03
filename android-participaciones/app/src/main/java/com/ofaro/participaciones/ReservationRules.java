package com.ofaro.participaciones;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

final class ReservationRules {
    private ReservationRules() {}
    static String validate(String name,String phone,String date,String time,int people){
        if(name==null||name.trim().length()<2)return "Escribe el nombre de la reserva.";
        String digits=phone==null?"":phone.replaceAll("[^0-9]","");
        if(digits.length()<9)return "Revisa el teléfono.";
        try{LocalDate.parse(date);}catch(DateTimeParseException e){return "La fecha debe tener formato AAAA-MM-DD.";}
        try{LocalTime.parse(time);}catch(DateTimeParseException e){return "La hora debe tener formato HH:MM.";}
        if(people<1||people>30)return "El número de personas debe estar entre 1 y 30.";
        return "";
    }
}
