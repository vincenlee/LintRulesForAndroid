package com.easygroup.ngaridoctor.library;

import io.reactivex.Observable;
import java.util.ArrayList;
import retrofit2.http.ArrayItem;
import retrofit2.http.NgariJsonPost;
import retrofit2.http.POST;

public interface HttpService {


	@POST("*.jsonRequest")
	@NgariJsonPost(serviceId = "eh.rankingInfoService", method = "rankThumbsUp")
	Observable<Doctor> getPatient(@ArrayItem String patientFeedback);


}