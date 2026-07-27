package dev.aaa1115910.bv.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.aaa1115910.bv.activities.user.LoginActivity
import dev.aaa1115910.bv.activities.user.UserSwitchActivity
import dev.aaa1115910.bv.viewmodel.UserViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PersonalContent(
    navFocusRequester: FocusRequester,
    userViewModel: UserViewModel = koinViewModel()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
            .focusRequester(navFocusRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (userViewModel.isLogin) {
            AsyncImage(
                model = userViewModel.face,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = userViewModel.username,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = {
                    context.startActivity(
                        android.content.Intent(context, UserSwitchActivity::class.java)
                    )
                }) {
                    Text("切换账号")
                }
                Spacer(modifier = Modifier.width(24.dp))
                Button(onClick = {
                    userViewModel.logout()
                }) {
                    Text("退出登录")
                }
            }
        } else {
            Text(
                text = "未登录",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(36.dp))
            Button(onClick = {
                context.startActivity(
                    android.content.Intent(context, LoginActivity::class.java)
                )
            }) {
                Text("登录")
            }
        }
    }
}
